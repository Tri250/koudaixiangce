#include "native_crash_handler.h"

#include <csignal>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <unistd.h>
#include <sys/syscall.h>
#include <fcntl.h>
#include <unwind.h>
#include <dlfcn.h>
#include <dirent.h>
#include <sys/stat.h>
#include <android/log.h>

#define LOG_TAG "NativeCrashHandler"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Constants ────────────────────────────────────────────────────────────────
static constexpr int kMaxSignals        = 7;
static constexpr int kMaxPathLen        = 512;
static constexpr int kMaxBacktraceDepth = 62;
static constexpr int kMaxLogFiles       = 20;

static const int kHandledSignals[kMaxSignals] = {
    SIGSEGV, SIGABRT, SIGFPE, SIGILL, SIGBUS, SIGTRAP, SIGSYS
};

// ── Global state (set during install, read-only in signal handler) ──────────
static char  g_crashLogDir[kMaxPathLen]      = {0};
static char  g_appVersion[128]               = {0};
static char  g_buildFingerprint[256]          = {0};
static struct sigaction g_oldActions[kMaxSignals];
static volatile sig_atomic_t g_installed = 0;

// ── Async-signal-safe helpers ───────────────────────────────────────────────

static const char* signalName(int sig) {
    switch (sig) {
        case SIGSEGV: return "SIGSEGV";
        case SIGABRT: return "SIGABRT";
        case SIGFPE:  return "SIGFPE";
        case SIGILL:  return "SIGILL";
        case SIGBUS:  return "SIGBUS";
        case SIGTRAP: return "SIGTRAP";
        case SIGSYS:  return "SIGSYS";
        default:      return "UNKNOWN";
    }
}

static size_t safeStrLen(const char* str) {
    if (str == nullptr) return 0;
    size_t len = 0;
    while (str[len] != '\0') len++;
    return len;
}

static void safeWriteStr(int fd, const char* str) {
    if (str == nullptr) return;
    size_t len = safeStrLen(str);
    if (len > 0) write(fd, str, len);
}

static void safeWriteInt(int fd, int value) {
    if (value == 0) {
        write(fd, "0", 1);
        return;
    }
    char buf[16];
    int pos = 0;
    if (value < 0) {
        write(fd, "-", 1);
        value = -value;
    }
    while (value > 0) {
        buf[pos++] = '0' + (value % 10);
        value /= 10;
    }
    for (int i = pos - 1; i >= 0; i--) {
        write(fd, &buf[i], 1);
    }
}

static void safeWriteLong(int fd, long value) {
    if (value == 0) {
        write(fd, "0", 1);
        return;
    }
    char buf[24];
    int pos = 0;
    if (value < 0) {
        write(fd, "-", 1);
        value = -value;
    }
    while (value > 0) {
        buf[pos++] = '0' + (value % 10);
        value /= 10;
    }
    for (int i = pos - 1; i >= 0; i--) {
        write(fd, &buf[i], 1);
    }
}

static void safeWriteHex(int fd, uintptr_t addr) {
    write(fd, "0x", 2);
    if (addr == 0) {
        write(fd, "0", 1);
        return;
    }
    char buf[16];
    int pos = 0;
    while (addr > 0) {
        int digit = addr & 0xF;
        buf[pos++] = (digit < 10) ? ('0' + digit) : ('a' + digit - 10);
        addr >>= 4;
    }
    for (int i = pos - 1; i >= 0; i--) {
        write(fd, &buf[i], 1);
    }
}

static void safeWriteNewline(int fd) {
    write(fd, "\n", 1);
}

// ── Backtrace using _Unwind_Backtrace ───────────────────────────────────────

struct BacktraceState {
    void** current;
    void** end;
};

static _Unwind_Reason_Code unwindCallback(struct _Unwind_Context* context, void* arg) {
    BacktraceState* state = static_cast<BacktraceState*>(arg);
    uintptr_t pc = _Unwind_GetIP(context);
    if (pc) {
        if (state->current == state->end) {
            return _URC_END_OF_STACK;
        }
        *state->current++ = reinterpret_cast<void*>(pc);
    }
    return _URC_NO_REASON;
}

static void safeWriteBacktrace(int fd) {
    void* buffer[kMaxBacktraceDepth];
    BacktraceState state = { buffer, buffer + kMaxBacktraceDepth };
    _Unwind_Backtrace(unwindCallback, &state);

    int frameCount = static_cast<int>(state.current - buffer);
    safeWriteStr(fd, "Stack trace:\n");
    for (int i = 0; i < frameCount; i++) {
        safeWriteInt(fd, i);
        safeWriteStr(fd, ": ");
        safeWriteHex(fd, reinterpret_cast<uintptr_t>(buffer[i]));
        safeWriteStr(fd, "  ");

        // Try to resolve symbol – dladdr may not be strictly async-signal-safe
        // but is widely used in practice and rarely fails.
        Dl_info info;
        if (dladdr(buffer[i], &info) && info.dli_sname) {
            safeWriteStr(fd, info.dli_sname);
            if (info.dli_fname) {
                safeWriteStr(fd, " (");
                // Write only the basename to keep output compact
                const char* fname = info.dli_fname;
                const char* slash = nullptr;
                for (const char* p = fname; *p; p++) {
                    if (*p == '/') slash = p;
                }
                safeWriteStr(fd, slash ? slash + 1 : fname);
                safeWriteStr(fd, ")");
            }
        } else {
            safeWriteStr(fd, "???");
        }
        safeWriteNewline(fd);
    }
}

// ── Signal handler ──────────────────────────────────────────────────────────

static void crashSignalHandler(int sig, siginfo_t* info, void* /*ucontext*/) {
    // Prevent recursive crash
    // Re-install default handler for this signal first
    struct sigaction sa;
    sa.sa_handler = SIG_DFL;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    for (int i = 0; i < kMaxSignals; i++) {
        sigaction(kHandledSignals[i], &sa, nullptr);
    }

    // Open crash log file
    char filePath[kMaxPathLen];
    // Build path: <crashLogDir>/native_crash_<timestamp>_<tid>.log
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);

    int dirLen = 0;
    while (g_crashLogDir[dirLen] != '\0' && dirLen < kMaxPathLen - 64) dirLen++;

    for (int i = 0; i < dirLen; i++) filePath[i] = g_crashLogDir[i];
    filePath[dirLen] = '/';
    int pos = dirLen + 1;

    const char* prefix = "native_crash_";
    for (int i = 0; prefix[i]; i++) filePath[pos++] = prefix[i];

    // Append timestamp (seconds)
    long sec = static_cast<long>(ts.tv_sec);
    char timeBuf[24];
    int timePos = 0;
    if (sec == 0) {
        timeBuf[timePos++] = '0';
    } else {
        long t = sec;
        while (t > 0) { timeBuf[timePos++] = '0' + (t % 10); t /= 10; }
    }
    for (int i = timePos - 1; i >= 0; i--) filePath[pos++] = timeBuf[i];

    filePath[pos++] = '_';
    pid_t tid = gettid();
    int tidVal = static_cast<int>(tid);
    if (tidVal == 0) {
        filePath[pos++] = '0';
    } else {
        char tidBuf[16];
        int tidPos = 0;
        int t = tidVal;
        while (t > 0) { tidBuf[tidPos++] = '0' + (t % 10); t /= 10; }
        for (int i = tidPos - 1; i >= 0; i--) filePath[pos++] = tidBuf[i];
    }
    filePath[pos++] = '.';
    filePath[pos++] = 'l';
    filePath[pos++] = 'o';
    filePath[pos++] = 'g';
    filePath[pos] = '\0';

    int fd = open(filePath, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd == -1) {
        // Fallback: try to write to a known location
        const char* fallback = "/data/local/tmp/native_crash.log";
        fd = open(fallback, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    }

    if (fd != -1) {
        safeWriteStr(fd, "=== RapidRAW Native Crash Report ===\n");
        safeWriteStr(fd, "Signal: ");
        safeWriteInt(fd, sig);
        safeWriteStr(fd, " (");
        safeWriteStr(fd, signalName(sig));
        safeWriteStr(fd, ")\n");

        safeWriteStr(fd, "Timestamp: ");
        safeWriteLong(fd, sec);
        safeWriteStr(fd, ".");
        safeWriteLong(fd, static_cast<long>(ts.tv_nsec));
        safeWriteStr(fd, " (epoch)\n");

        safeWriteStr(fd, "Thread ID: ");
        safeWriteInt(fd, static_cast<int>(tid));
        safeWriteNewline(fd);

        if (info) {
            safeWriteStr(fd, "Fault address: ");
            safeWriteHex(fd, reinterpret_cast<uintptr_t>(info->si_addr));
            safeWriteNewline(fd);
            safeWriteStr(fd, "Sending PID: ");
            safeWriteInt(fd, static_cast<int>(info->si_pid));
            safeWriteNewline(fd);
        }

        safeWriteStr(fd, "Build fingerprint: ");
        safeWriteStr(fd, g_buildFingerprint);
        safeWriteNewline(fd);

        safeWriteStr(fd, "App version: ");
        safeWriteStr(fd, g_appVersion);
        safeWriteNewline(fd);

        safeWriteNewline(fd);
        safeWriteBacktrace(fd);

        close(fd);
    }

    // Re-raise the signal with default handler so the app still crashes normally
    raise(sig);
}

// ── JNI: installNativeHandler ───────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_rapidraw_core_NativeCrashHandler_installNativeHandler(
    JNIEnv *env,
    jobject /*thiz*/,
    jstring crashLogDir,
    jstring appVersion,
    jstring buildFingerprint
) {
    if (g_installed) {
        LOGI("Native crash handler already installed");
        return JNI_TRUE;
    }

    // Copy crash log directory path
    if (crashLogDir) {
        const char* cPath = env->GetStringUTFChars(crashLogDir, nullptr);
        if (cPath) {
            size_t len = safeStrLen(cPath);
            if (len >= kMaxPathLen) len = kMaxPathLen - 1;
            memcpy(g_crashLogDir, cPath, len);
            g_crashLogDir[len] = '\0';
            env->ReleaseStringUTFChars(crashLogDir, cPath);
        }
    }
    if (g_crashLogDir[0] == '\0') {
        // Default fallback
        memcpy(g_crashLogDir, "/data/local/tmp", 15);
        g_crashLogDir[15] = '\0';
    }

    // Copy app version
    if (appVersion) {
        const char* cVer = env->GetStringUTFChars(appVersion, nullptr);
        if (cVer) {
            size_t len = safeStrLen(cVer);
            if (len >= sizeof(g_appVersion)) len = sizeof(g_appVersion) - 1;
            memcpy(g_appVersion, cVer, len);
            g_appVersion[len] = '\0';
            env->ReleaseStringUTFChars(appVersion, cVer);
        }
    }
    if (g_appVersion[0] == '\0') {
        memcpy(g_appVersion, "unknown", 7);
        g_appVersion[7] = '\0';
    }

    // Copy build fingerprint
    if (buildFingerprint) {
        const char* cFp = env->GetStringUTFChars(buildFingerprint, nullptr);
        if (cFp) {
            size_t len = safeStrLen(cFp);
            if (len >= sizeof(g_buildFingerprint)) len = sizeof(g_buildFingerprint) - 1;
            memcpy(g_buildFingerprint, cFp, len);
            g_buildFingerprint[len] = '\0';
            env->ReleaseStringUTFChars(buildFingerprint, cFp);
        }
    }
    if (g_buildFingerprint[0] == '\0') {
        memcpy(g_buildFingerprint, "unknown", 7);
        g_buildFingerprint[7] = '\0';
    }

    // Install signal handlers
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crashSignalHandler;
    sa.sa_flags = SA_SIGINFO | SA_RESTART;
    sigemptyset(&sa.sa_mask);

    for (int i = 0; i < kMaxSignals; i++) {
        int sig = kHandledSignals[i];
        if (sigaction(sig, &sa, &g_oldActions[i]) != 0) {
            LOGE("Failed to install handler for signal %d (%s)", sig, signalName(sig));
            // Rollback: restore previously installed handlers
            for (int j = 0; j < i; j++) {
                sigaction(kHandledSignals[j], &g_oldActions[j], nullptr);
            }
            return JNI_FALSE;
        }
    }

    g_installed = 1;
    LOGI("Native crash handler installed for 7 signals, logs dir: %s", g_crashLogDir);
    return JNI_TRUE;
}

// ── JNI: getCrashLogs ───────────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_rapidraw_core_NativeCrashHandler_getCrashLogs(
    JNIEnv *env,
    jobject /*thiz*/
) {
    const char* dirPath = (g_crashLogDir[0] != '\0') ? g_crashLogDir : "/data/local/tmp";
    DIR* dir = opendir(dirPath);
    if (dir == nullptr) {
        return env->NewStringUTF("[]");
    }

    // Collect native crash log files
    struct {
        char name[256];
        time_t mtime;
    } entries[kMaxLogFiles];
    int entryCount = 0;

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr && entryCount < kMaxLogFiles) {
        const char* name = entry->d_name;
        // Match "native_crash_" prefix and ".log" suffix
        if (strncmp(name, "native_crash_", 13) == 0) {
            size_t len = safeStrLen(name);
            if (len > 4 && strcmp(name + len - 4, ".log") == 0) {
                size_t nameLen = len < 255 ? len : 255;
                memcpy(entries[entryCount].name, name, nameLen);
                entries[entryCount].name[nameLen] = '\0';

                // Get mtime
                char fullPath[kMaxPathLen];
                snprintf(fullPath, sizeof(fullPath), "%s/%s", dirPath, name);
                struct stat st;
                entries[entryCount].mtime = (stat(fullPath, &st) == 0) ? st.st_mtime : 0;
                entryCount++;
            }
        }
    }
    closedir(dir);

    // Sort by mtime descending (newest first)
    for (int i = 0; i < entryCount - 1; i++) {
        for (int j = i + 1; j < entryCount; j++) {
            if (entries[j].mtime > entries[i].mtime) {
                auto tmp = entries[i];
                entries[i] = entries[j];
                entries[j] = tmp;
            }
        }
    }

    // Build JSON array string
    char jsonBuf[8192];
    int pos = 0;
    jsonBuf[pos++] = '[';

    for (int i = 0; i < entryCount; i++) {
        if (i > 0) jsonBuf[pos++] = ',';
        jsonBuf[pos++] = '"';
        size_t nameLen = safeStrLen(entries[i].name);
        if (pos + static_cast<int>(nameLen) + 2 < static_cast<int>(sizeof(jsonBuf))) {
            memcpy(jsonBuf + pos, entries[i].name, nameLen);
            pos += static_cast<int>(nameLen);
        }
        jsonBuf[pos++] = '"';
    }

    jsonBuf[pos++] = ']';
    jsonBuf[pos] = '\0';

    return env->NewStringUTF(jsonBuf);
}