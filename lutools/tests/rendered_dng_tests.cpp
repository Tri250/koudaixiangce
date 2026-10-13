#include "core/rendered_dng.h"

#include <tiffio.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <fstream>
#include <filesystem>
#include <iostream>
#include <iterator>
#include <limits>
#include <string>
#include <vector>

#ifndef _WIN32
#include <csignal>
#include <sys/resource.h>
#include <sys/wait.h>
#include <unistd.h>
#endif

namespace {

constexpr uint16_t kLinearRaw = 34892;
constexpr const char* kStagingMarker = ".rawlab-rendered-dng-";

int failures = 0;

void check(bool condition, const std::string& name) {
    std::cout << (condition ? "PASS " : "FAIL ") << name << '\n';
    if (!condition) ++failures;
}

constexpr TIFFFieldInfo kCustomFields[] = {
    {TIFFTAG_COLORIMETRICREFERENCE, 1, 1, TIFF_SHORT, FIELD_CUSTOM, 1, 0,
     const_cast<char*>("ColorimetricReference")},
    {TIFFTAG_PROFILENAME, TIFF_VARIABLE, TIFF_VARIABLE, TIFF_ASCII, FIELD_CUSTOM, 1, 0,
     const_cast<char*>("ProfileName")},
    {TIFFTAG_PROFILETONECURVE, TIFF_VARIABLE, TIFF_VARIABLE, TIFF_FLOAT, FIELD_CUSTOM, 1, 1,
     const_cast<char*>("ProfileToneCurve")},
    {TIFFTAG_FORWARDMATRIX1, TIFF_VARIABLE, TIFF_VARIABLE, TIFF_SRATIONAL, FIELD_CUSTOM, 1, 1,
     const_cast<char*>("ForwardMatrix1")},
    {TIFFTAG_BASELINEEXPOSUREOFFSET, 1, 1, TIFF_SRATIONAL, FIELD_CUSTOM, 1, 0,
     const_cast<char*>("BaselineExposureOffset")},
    {TIFFTAG_DEFAULTBLACKRENDER, 1, 1, TIFF_LONG, FIELD_CUSTOM, 1, 0,
     const_cast<char*>("DefaultBlackRender")},
};

bool registerCustomFields(TIFF* tif) {
    return TIFFMergeFieldInfo(tif, kCustomFields,
                              static_cast<uint32_t>(std::size(kCustomFields))) == 0;
}

TIFFExtendProc previousTagExtender = nullptr;

void registerCustomFieldsBeforeRead(TIFF* tif) {
    if (previousTagExtender) previousTagExtender(tif);
    TIFFMergeFieldInfo(tif, kCustomFields,
                       static_cast<uint32_t>(std::size(kCustomFields)));
}

float inverseSrgb(float encoded) {
    const double value = std::clamp(static_cast<double>(encoded), 0.0, 1.0);
    return static_cast<float>(value <= 0.04045
        ? value / 12.92
        : std::pow((value + 0.055) / 1.055, 2.4));
}

std::filesystem::path testDirectory() {
    const auto stamp = std::chrono::steady_clock::now().time_since_epoch().count();
    const auto directory = std::filesystem::temp_directory_path() /
        ("rawtools-rendered-dng-" + std::to_string(stamp));
    std::filesystem::create_directories(directory);
    return directory;
}

bool samePixels(const sony2fuji::ImageData& left, const sony2fuji::ImageData& right) {
    if (left.width != right.width || left.height != right.height ||
        left.pixels.size() != right.pixels.size()) return false;
    for (size_t i = 0; i < left.pixels.size(); ++i) {
        if (left.pixels[i].r != right.pixels[i].r ||
            left.pixels[i].g != right.pixels[i].g ||
            left.pixels[i].b != right.pixels[i].b) return false;
    }
    return true;
}

std::string readFile(const std::filesystem::path& path) {
    std::ifstream input(path, std::ios::binary);
    return std::string(std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>());
}

void writeFile(const std::filesystem::path& path, const std::string& contents) {
    std::ofstream output(path, std::ios::binary | std::ios::trunc);
    output.write(contents.data(), static_cast<std::streamsize>(contents.size()));
}

bool hasStagingFile(const std::filesystem::path& directory) {
    for (const auto& entry : std::filesystem::directory_iterator(directory)) {
#ifdef _WIN32
        const auto filename = entry.path().filename().string();
        if (filename.rfind("rld", 0) == 0 && entry.path().extension() == ".tmp")
#else
        if (entry.path().filename().string().find(kStagingMarker) != std::string::npos)
#endif
            return true;
    }
    return false;
}

sony2fuji::ImageData smallImage() {
    using sony2fuji::ImageData;
    using sony2fuji::RGB;
    ImageData image(2, 2);
    image.pixels = {
        RGB(0.1f, 0.2f, 0.3f), RGB(0.4f, 0.5f, 0.6f),
        RGB(0.7f, 0.8f, 0.9f), RGB(1.0f, 0.0f, 0.5f),
    };
    return image;
}

void testRenderedDng(const std::filesystem::path& directory) {
    using sony2fuji::ErrorCode;
    using sony2fuji::ImageData;
    using sony2fuji::RGB;

    ImageData image(4, 2);
    image.pixels = {
        RGB(0, 0.5f, 1), RGB(-0.25f, 0.04045f, 1.25f),
        RGB(0.0031308f, 0.18f, 0.9f), RGB(0.25f, 0.75f, 0.5f),
        RGB(1, 0, 0.5f), RGB(0.1f, 0.2f, 0.3f),
        RGB(0.8f, 0.6f, 0.4f), RGB(0.01f, 0.02f, 0.03f),
    };
    const ImageData original = image;
    const auto output = directory / "rendered.dng";
    check(sony2fuji::saveRenderedDNG(image, output.string()) == ErrorCode::Success,
          "rendered DNG writes synthetic final sRGB image");
    check(samePixels(image, original), "rendered DNG does not mutate source pixels");

    previousTagExtender = TIFFSetTagExtender(registerCustomFieldsBeforeRead);
    TIFF* tif = TIFFOpen(output.string().c_str(), "r");
    TIFFSetTagExtender(previousTagExtender);
    previousTagExtender = nullptr;
    check(tif != nullptr, "libtiff independently opens rendered DNG");
    if (!tif) return;
    check(TIFFFindField(tif, TIFFTAG_COLORIMETRICREFERENCE, TIFF_ANY) != nullptr,
          "libtiff registers DNG 1.2/1.4 fields independently");

    uint32_t width = 0, height = 0;
    uint16_t bits = 0, samples = 0, photometric = 0, orientation = 0;
    uint16_t planar = 0, sampleFormat = 0;
    check(TIFFGetField(tif, TIFFTAG_IMAGEWIDTH, &width) && width == 4,
          "DNG width is exact");
    check(TIFFGetField(tif, TIFFTAG_IMAGELENGTH, &height) && height == 2,
          "DNG height is exact");
    check(TIFFGetField(tif, TIFFTAG_BITSPERSAMPLE, &bits) && bits == 32,
          "DNG stores float32 samples");
    check(TIFFGetField(tif, TIFFTAG_SAMPLESPERPIXEL, &samples) && samples == 3,
          "DNG stores three channels");
    check(TIFFGetField(tif, TIFFTAG_PHOTOMETRIC, &photometric) && photometric == kLinearRaw,
          "DNG uses LinearRaw photometric interpretation");
    check(TIFFGetField(tif, TIFFTAG_ORIENTATION, &orientation) && orientation == ORIENTATION_TOPLEFT,
          "DNG orientation is top-left");
    check(TIFFGetField(tif, TIFFTAG_PLANARCONFIG, &planar) && planar == PLANARCONFIG_CONTIG,
          "DNG stores contiguous RGB samples");
    check(TIFFGetField(tif, TIFFTAG_SAMPLEFORMAT, &sampleFormat) && sampleFormat == SAMPLEFORMAT_IEEEFP,
          "DNG sample format is IEEE floating point");
    uint16_t extraCount = 0;
    uint16_t* extraTypes = nullptr;
    check(!TIFFGetField(tif, TIFFTAG_EXTRASAMPLES, &extraCount, &extraTypes),
          "DNG has no ExtraSamples tag");

    uint16_t colorimetricReference = 0;
    check(TIFFGetField(tif, TIFFTAG_COLORIMETRICREFERENCE, &colorimetricReference) &&
          colorimetricReference == 1, "DNG ColorimetricReference is ICC PCS");
    char* profileName = nullptr;
    check(TIFFGetField(tif, TIFFTAG_PROFILENAME, &profileName) && profileName &&
          std::string(profileName) == "RawLab Baked sRGB", "DNG profile name is neutral");
    uint16_t toneCount = 0;
    const float* toneCurve = nullptr;
    check(TIFFGetField(tif, TIFFTAG_PROFILETONECURVE, &toneCount, &toneCurve) &&
          toneCount == 4 && toneCurve && toneCurve[0] == 0 && toneCurve[1] == 0 &&
          toneCurve[2] == 1 && toneCurve[3] == 1, "DNG profile tone curve is identity");
    uint16_t forwardCount = 0;
    const float* forwardMatrix = nullptr;
    check(TIFFGetField(tif, TIFFTAG_FORWARDMATRIX1, &forwardCount, &forwardMatrix) &&
          forwardCount == 9 && forwardMatrix &&
          std::abs(forwardMatrix[0] - 0.43606575f) < 1e-5f &&
          std::abs(forwardMatrix[8] - 0.71409936f) < 1e-5f,
          "DNG forward matrix is the virtual linear-sRGB D50 matrix");
    float baselineOffset = 99;
    uint32_t defaultBlackRender = 0;
    check(TIFFGetField(tif, TIFFTAG_BASELINEEXPOSUREOFFSET, &baselineOffset) &&
          baselineOffset == 0, "DNG baseline exposure offset is zero");
    check(TIFFGetField(tif, TIFFTAG_DEFAULTBLACKRENDER, &defaultBlackRender) &&
          defaultBlackRender == 1, "DNG DefaultBlackRender is None");

    uint32_t xmpCount = 0;
    const void* xmpData = nullptr;
    check(TIFFGetField(tif, TIFFTAG_XMLPACKET, &xmpCount, &xmpData) && xmpData &&
          std::string(static_cast<const char*>(xmpData), xmpCount).find(
              "crs:Sharpness=\"0\"") != std::string::npos,
          "DNG contains neutral Adobe XMP");

    bool pixelsMatch = true;
    std::vector<float> row(static_cast<size_t>(width) * 3);
    for (uint32_t y = 0; y < height && pixelsMatch; ++y) {
        if (TIFFReadScanline(tif, row.data(), y, 0) < 0) {
            pixelsMatch = false;
            break;
        }
        for (uint32_t x = 0; x < width; ++x) {
            const auto& source = original.at(static_cast<int>(x), static_cast<int>(y));
            const size_t offset = static_cast<size_t>(x) * 3;
            pixelsMatch = std::abs(row[offset] - inverseSrgb(source.r)) < 1e-6f &&
                std::abs(row[offset + 1] - inverseSrgb(source.g)) < 1e-6f &&
                std::abs(row[offset + 2] - inverseSrgb(source.b)) < 1e-6f;
            if (!pixelsMatch) break;
        }
    }
    check(pixelsMatch, "DNG float pixels inverse-decode clamped sRGB exactly");
    TIFFClose(tif);
}

void testRejectsInvalidInput(const std::filesystem::path& directory) {
    using sony2fuji::ErrorCode;
    using sony2fuji::ImageData;
    using sony2fuji::RGB;
    const auto output = directory / "invalid.dng";

    ImageData empty;
    check(sony2fuji::saveRenderedDNG(empty, output.string()) == ErrorCode::ProcessingError,
          "DNG rejects empty dimensions");
    ImageData mismatched(2, 2);
    mismatched.pixels.pop_back();
    check(sony2fuji::saveRenderedDNG(mismatched, output.string()) == ErrorCode::ProcessingError,
          "DNG rejects mismatched pixel count");
    ImageData nonfinite(1, 1);
    nonfinite.pixels[0] = RGB(std::numeric_limits<float>::quiet_NaN(), 0, 0);
    check(sony2fuji::saveRenderedDNG(nonfinite, output.string()) == ErrorCode::ProcessingError,
          "DNG rejects NaN pixels");
    nonfinite.pixels[0] = RGB(0, std::numeric_limits<float>::infinity(), 0);
    check(sony2fuji::saveRenderedDNG(nonfinite, output.string()) == ErrorCode::ProcessingError,
          "DNG rejects infinite pixels");
    ImageData valid(1, 1);
    check(sony2fuji::saveRenderedDNG(valid, (directory / "missing" / "out.dng").string()) ==
              ErrorCode::IOError,
          "DNG reports an unwritable output path");
    check(!std::filesystem::exists(output), "invalid DNG inputs do not create output");
}

void testAtomicStorage(const std::filesystem::path& directory) {
    using sony2fuji::ErrorCode;

#ifdef _WIN32
    const auto stagingProbe = directory / "rld-staging-probe.tmp";
    writeFile(stagingProbe, "staging probe");
    check(hasStagingFile(directory), "Windows staging scan detects native rld temporary files");
    std::filesystem::remove(stagingProbe);
#endif

    const auto target = directory / "replace-existing.dng";
    writeFile(target, "old target");
    check(sony2fuji::saveRenderedDNG(smallImage(), target.string()) == ErrorCode::Success,
          "DNG publishes a valid replacement");
    check(readFile(target) != "old target", "successful DNG export replaces existing target");
    check(!hasStagingFile(directory), "successful DNG export leaves no staging file");

    const auto blockedDirectory = directory / "publication-blocked";
    std::filesystem::create_directory(blockedDirectory);
    const auto marker = blockedDirectory / "keep.txt";
    writeFile(marker, "keep directory contents");
    check(sony2fuji::saveRenderedDNG(smallImage(), blockedDirectory.string()) ==
              ErrorCode::IOError,
          "DNG reports failed publication as an I/O error");
    check(std::filesystem::is_directory(blockedDirectory) && readFile(marker) ==
              "keep directory contents",
          "failed DNG publication preserves an existing directory and its contents");
    check(!hasStagingFile(directory), "failed DNG publication removes its staging file");

    const auto invalidTarget = directory / "invalid-preserves-target.dng";
    writeFile(invalidTarget, "preserve this target");
    auto invalid = smallImage();
    invalid.pixels.front().r = std::numeric_limits<float>::quiet_NaN();
    check(sony2fuji::saveRenderedDNG(invalid, invalidTarget.string()) ==
              ErrorCode::ProcessingError,
          "DNG rejects invalid pixels before opening a staging file");
    check(readFile(invalidTarget) == "preserve this target",
          "invalid DNG pixels preserve an existing target");
    check(!hasStagingFile(directory), "invalid DNG pixels leave no staging file");
}

#ifndef _WIN32
void testMidwriteFailure(const std::filesystem::path& directory) {
    using sony2fuji::ErrorCode;

    const auto target = directory / "midwrite-preserves-target.dng";
    writeFile(target, "preserve after write failure");
    sony2fuji::ImageData image(128, 128);
    for (size_t index = 0; index < image.pixels.size(); ++index) {
        const float value = static_cast<float>(index % 256) / 255.0f;
        image.pixels[index] = sony2fuji::RGB(value, 1.0f - value, 0.5f);
    }

    const pid_t child = fork();
    if (child < 0) {
        check(false, "POSIX mid-write failure test can fork");
        return;
    }
    if (child == 0) {
        std::signal(SIGXFSZ, SIG_IGN);
        const struct rlimit limit = {4096, 4096};
        if (setrlimit(RLIMIT_FSIZE, &limit) != 0) _exit(2);
        const ErrorCode result = sony2fuji::saveRenderedDNG(image, target.string());
        _exit(result == ErrorCode::IOError ? 0 : 1);
    }

    int status = 0;
    const bool waited = waitpid(child, &status, 0) == child;
    check(waited && WIFEXITED(status) && WEXITSTATUS(status) == 0,
          "mid-write DNG failure returns an I/O error");
    check(readFile(target) == "preserve after write failure",
          "mid-write DNG failure preserves an existing target");
    check(!hasStagingFile(directory), "mid-write DNG failure removes its staging file");
}
#endif

} // namespace

int main() {
    const auto directory = testDirectory();
    testRenderedDng(directory);
    testRejectsInvalidInput(directory);
    testAtomicStorage(directory);
#ifndef _WIN32
    testMidwriteFailure(directory);
#endif
    std::filesystem::remove_all(directory);
    return failures == 0 ? 0 : 1;
}
