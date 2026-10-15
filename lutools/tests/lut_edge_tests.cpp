// =============================================================================
// lut_edge_tests.cpp — LUTParser / LUTApplicator 边界与异常场景
// -----------------------------------------------------------------------------
// 覆盖 TestPlan TC-LUT-008 / 009 / 010 / 011 等用例。
// 所有测试合成 cube 文件，不依赖外部夹具。
// =============================================================================

#include "sony2fuji/sony2fuji.h"
#include "sony2fuji/ffi/sony2fuji_c.h"
#include <cmath>
#include <fstream>
#include <iostream>
#include <string>
#include <filesystem>

using namespace sony2fuji;

static int failures = 0;
static int total = 0;

static void check(bool ok, const char* name) {
    ++total;
    std::cout << (ok ? "PASS " : "FAIL ") << name << '\n';
    if (!ok) ++failures;
}

static bool file_contains(const std::string& path, const std::string& content) {
    std::ofstream f(path, std::ios::binary | std::ios::trunc);
    if (!f.good()) return false;
    f.write(content.data(), content.size());
    return true;
}

// -----------------------------------------------------------------------------
// 1. 基础合法性断言
// -----------------------------------------------------------------------------
static std::string good_17_cube() {
    // 17-grid identity LUT, output = input, 2 bytes + 17*17*17 = 4913 data lines
    std::string s = "#title:Identity-17\n#FUJIFILM Test\n\nLUT_3D_SIZE 17\n\n";
    for (int i = 0; i < 17 * 17 * 17; ++i) {
        // triplet index → RGB, normalized to [0,1]
        int idx = i;
        int b = idx % 17; idx /= 17;
        int g = idx % 17; idx /= 17;
        int r = idx;
        s += std::to_string(r / 16.0f) + " " +
             std::to_string(g / 16.0f) + " " +
             std::to_string(b / 16.0f) + "\n";
    }
    return s;
}

static void test_good_17() {
    const std::string path = "/tmp/good_17.cube";
    file_contains(path, good_17_cube());
    auto lut = LUTParser::loadLUT(path);
    check(lut != nullptr, "17-grid identity LUT loads");
    check(lut && lut->isValid(), "17-grid LUT isValid()");
    check(lut && lut->getSize() == 17, "17-grid LUT size == 17");
    if (!lut) return;
    // 三线性插值端点等于 cube corner
    LUTApplicator app(std::shared_ptr<LUT3D>(std::move(lut)));
    RGB corner000 = app.apply({0.0f, 0.0f, 0.0f});
    RGB corner111 = app.apply({1.0f, 1.0f, 1.0f});
    check(std::abs(corner000.r - 0.0f) < 1e-4f &&
          std::abs(corner000.g - 0.0f) < 1e-4f &&
          std::abs(corner000.b - 0.0f) < 1e-4f, "17-grid corner [0,0,0] exact");
    check(std::abs(corner111.r - 1.0f) < 1e-4f &&
          std::abs(corner111.g - 1.0f) < 1e-4f &&
          std::abs(corner111.b - 1.0f) < 1e-4f, "17-grid corner [1,1,1] exact");
}

// -----------------------------------------------------------------------------
// TC-LUT-008 / 009 — 损坏或空 cube
// -----------------------------------------------------------------------------
static void test_empty_no_lutsize() {
    const std::string path = "/tmp/empty_nosize.cube";
    file_contains(path, "#title: empty\n#nothing\n");
    auto lut = LUTParser::loadLUT(path);
    check(!lut || !lut->isValid(), "empty .cube (no LUT_3D_SIZE) rejected");
}

static void test_empty_size_only() {
    const std::string path = "/tmp/empty_size_only.cube";
    file_contains(path, "#title: empty\nLUT_3D_SIZE 17\n\n");
    auto lut = LUTParser::loadLUT(path);
    check(!lut || !lut->isValid(), "size-only .cube rejected");
}

static void test_binary_noise() {
    const std::string path = "/tmp/noise.cube";
    std::string content = { 'T','I','F','F',0x00,0x00,0x2A,0x00,'\xde','\xad','\xbe','\xef' };
    file_contains(path, content);
    auto lut = LUTParser::loadLUT(path);
    check(!lut || !lut->isValid(), "binary-noise .cube rejected");
}

static void test_truncated_data() {
    // Write valid header + size + half the data
    const std::string path = "/tmp/truncated.cube";
    std::string body = good_17_cube();
    // keep header + size + only 1/4 of data lines
    auto header_end = body.find("LUT_3D_SIZE");
    auto nl = body.find('\n', header_end + 10);
    std::string head = body.substr(0, nl + 2);
    auto data_begin = body.find('\n', nl + 1) + 1;
    auto full_data = body.substr(data_begin);
    size_t half = full_data.size() / 4;
    file_contains(path, head + full_data.substr(0, half));
    auto lut = LUTParser::loadLUT(path);
    check(!lut || !lut->isValid(), "truncated cube rejected");
}

// -----------------------------------------------------------------------------
// TC-LUT-010 — 非标准 grid size（2、5、13 等）
// -----------------------------------------------------------------------------
static void make_nonstandard_cube(const std::string& path, int size) {
    std::string s = "#title:weird\n\nLUT_3D_SIZE " + std::to_string(size) + "\n\n";
    for (int i = 0; i < size * size * size; ++i) {
        int idx = i;
        int b = idx % size; idx /= size;
        int g = idx % size; idx /= size;
        int r = idx;
        s += std::to_string(r / (double)(size - 1)) + " " +
             std::to_string(g / (double)(size - 1)) + " " +
             std::to_string(b / (double)(size - 1)) + "\n";
    }
    file_contains(path, s);
}

static void test_nonstandard_sizes() {
    for (int size : {2, 5, 13}) {
        const std::string path = "/tmp/nonstandard_" + std::to_string(size) + ".cube";
        make_nonstandard_cube(path, size);
        auto lut = LUTParser::loadLUT(path);
        check(lut && lut->isValid() && lut->getSize() == size,
              (std::string("nonstandard size ") + std::to_string(size) + " accepted").c_str());
    }
}

// -----------------------------------------------------------------------------
// TC-LUT-011 — 越界 domain 值
// -----------------------------------------------------------------------------
static void test_domain_outside_unit() {
    // Generate a valid 17-grid but put LUT values outside [0,1]
    std::string s = "#title:outside\n\nLUT_3D_SIZE 4\n\n";
    for (int i = 0; i < 4 * 4 * 4; ++i) {
        s += "2.0 -0.5 1.5\n";  // deliberately outside unit interval
    }
    const std::string path = "/tmp/domain_outside.cube";
    file_contains(path, s);
    auto lut = LUTParser::loadLUT(path);
    check(lut != nullptr && lut->isValid(), "LUT with out-of-domain values still loads (clamp caller's job)");
    if (!lut) return;
    LUTApplicator app(std::shared_ptr<LUT3D>(std::move(lut)));
    RGB out = app.apply({0.5f, 0.5f, 0.5f});
    check(!(std::isnan(out.r) || std::isnan(out.g) || std::isnan(out.b)),
          "LUT with out-of-domain values produces no NaN");
}

// -----------------------------------------------------------------------------
// TC-LUT-CACHE — LUTParser::loadLUTCached 缓存语义
// -----------------------------------------------------------------------------
static void test_lut_cache() {
    const std::string path = "/tmp/good_17_for_cache.cube";
    file_contains(path, good_17_cube());

    auto a = LUTParser::loadLUTCached(path);
    auto b = LUTParser::loadLUTCached(path);
    check(a != nullptr && b != nullptr, "cached loads return non-null");
    check(a.get() == b.get(), "same pointer returned by loadLUTCached");
    check(a.use_count() >= 2, "shared_ptr refcount >= 2 after two loads");
}

// -----------------------------------------------------------------------------
// TC-C-ERR-004 / TC-C-ERR-005 — public C API rejects bad LUT
// -----------------------------------------------------------------------------
static void test_c_api_rejects_bad_lut() {
    sony2fuji_session* session = nullptr;
    sony2fuji_session_create(&session);
    sony2fuji_request req{};
    req.version = SONY2FUJI_REQUEST_VERSION;
    req.struct_size = sizeof(req);
    req.input_type = SONY2FUJI_INPUT_BUFFER;
    static const unsigned char pixel_buf[] = { 128, 128, 128, 255 };
    req.input_pixels = pixel_buf;
    req.input_width = req.input_height = 1;
    req.input_pixel_format = SONY2FUJI_PIXEL_RGBA8;
    req.input_color_space = SONY2FUJI_COLOR_SRGB;
    req.input_is_linear = 0;
    req.lut_strength = 1;
    req.output_target = SONY2FUJI_TARGET_BUFFER;
    req.output_format = SONY2FUJI_OUTPUT_RGBA8;
    req.size_mode = SONY2FUJI_SIZE_EXACT;
    req.target_width = req.target_height = 1;
    req.wb_mode = SONY2FUJI_WB_CAMERA;
    req.temperature = 6500;
    req.brightness = req.contrast = req.saturation = 1;

    // Non-existent LUT path
    req.lut_path = "/tmp/absolutely_no_such_lut_xyz.cube";
    req.lut_strength = 1.0f;
    sony2fuji_buffer buf{};
    auto s1 = sony2fuji_process(session, &req, &buf);
    check(s1 != SONY2FUJI_STATUS_OK, "C API rejects non-existent LUT path");

    // Empty file that happens to look like a valid .cube header (but size 0 data)
    req.lut_path = "/tmp/empty_nosize.cube";
    req.lut_strength = 1.0f;
    auto s2 = sony2fuji_process(session, &req, &buf);
    check(s2 != SONY2FUJI_STATUS_OK, "C API rejects empty LUT content");

    sony2fuji_session_destroy(session);
}

int main() {
    test_good_17();
    test_empty_no_lutsize();
    test_empty_size_only();
    test_binary_noise();
    test_truncated_data();
    test_nonstandard_sizes();
    test_domain_outside_unit();
    test_lut_cache();
    test_c_api_rejects_bad_lut();

    std::cout << "\n===== Summary: " << (total - failures) << "/" << total
              << " PASS";
    if (failures) std::cout << "  ❌ " << failures << " FAIL";
    std::cout << " =====\n";
    return failures ? 1 : 0;
}
