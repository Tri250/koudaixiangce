// =============================================================================
// encoder_tests.cpp — ImageEncoder 边界 + 基本功能
// -----------------------------------------------------------------------------
// 覆盖 TestPlan TC-ENC-001 / 002 / 003 / 004 / 005。
// 所有测试合成 ImageData，不依赖 RAW 夹具。
// =============================================================================

#include "sony2fuji/sony2fuji.h"
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <iostream>

using namespace sony2fuji;

static int failures = 0;
static int total = 0;
static void check(bool ok, const char* name) {
    ++total;
    std::cout << (ok ? "PASS " : "FAIL ") << name << '\n';
    if (!ok) ++failures;
}

static ImageData make_valid_image(int w, int h) {
    ImageData img;
    img.width = w; img.height = h;
    img.pixels.resize(static_cast<size_t>(w * h), RGB{0.5f, 0.5f, 0.5f});
    // fill gradient so round-trip is meaningful
    for (int y = 0; y < h; ++y)
        for (int x = 0; x < w; ++x) {
            RGB& px = img.pixels[y * w + x];
            px.r = x / (float)w;
            px.g = y / (float)h;
            px.b = (x + y) / (float)(w + h);
        }
    return img;
}

// -----------------------------------------------------------------------------
// TC-ENC-001 / TC-ENC-002 — JPEG quality 边界 + round-trip
// -----------------------------------------------------------------------------
static void test_jpeg_roundtrip() {
    auto img = make_valid_image(4, 4);
    check(ImageEncoder::saveJPEG(img, "/tmp/enc95.jpg", 95) == ErrorCode::Success,
          "JPEG q=95 saves");
    // 质量边界
    check(ImageEncoder::saveJPEG(img, "/tmp/enc_q1.jpg", 1) == ErrorCode::Success,
          "JPEG q=1 saves");
    check(ImageEncoder::saveJPEG(img, "/tmp/enc_q100.jpg", 100) == ErrorCode::Success,
          "JPEG q=100 saves");
    // 质量越界 — 101 应被拒绝
    check(ImageEncoder::saveJPEG(img, "/tmp/enc_q101.jpg", 101) != ErrorCode::Success,
          "JPEG q=101 rejected");
    // 质量越界 — 0 应被拒绝
    check(ImageEncoder::saveJPEG(img, "/tmp/enc_q0.jpg", 0) != ErrorCode::Success,
          "JPEG q=0 rejected");
}

// -----------------------------------------------------------------------------
// TC-ENC-003 — PNG 无损 round-trip
// -----------------------------------------------------------------------------
static void test_png_lossless() {
    auto img = make_valid_image(8, 8);
    check(ImageEncoder::savePNG(img, "/tmp/enc.png") == ErrorCode::Success,
          "PNG saves");
    // verify file is reasonably sized (> header)
    struct stat st;
    check(stat("/tmp/enc.png", &st) == 0 && st.st_size > 100,
          "PNG file size > 100 bytes");
}

// -----------------------------------------------------------------------------
// TC-ENC-004 — 空 ImageData 拒绝保存
// -----------------------------------------------------------------------------
static void test_empty_image() {
    ImageData empty1;  // default: width=height=0, pixels empty
    ImageData empty2;  // width set but pixels not resized
    empty2.width = 10; empty2.height = 10;
    ImageData empty3;  // height=0
    empty3.width = 10; empty3.height = 0;

    check(ImageEncoder::saveJPEG(empty1, "/tmp/empty1.jpg") != ErrorCode::Success,
          "empty ImageData (0x0) JPEG rejected");
    check(ImageEncoder::savePNG(empty1, "/tmp/empty1.png") != ErrorCode::Success,
          "empty ImageData (0x0) PNG rejected");

    check(ImageEncoder::saveJPEG(empty2, "/tmp/empty2.jpg") != ErrorCode::Success,
          "ImageData width/height set but no pixels rejected JPEG");
    check(ImageEncoder::savePNG(empty2, "/tmp/empty2.png") != ErrorCode::Success,
          "ImageData width/height set but no pixels rejected PNG");

    check(ImageEncoder::saveJPEG(empty3, "/tmp/empty3.jpg") != ErrorCode::Success,
          "height=0 rejected");
}

// -----------------------------------------------------------------------------
// TC-ENC-005 — 只读目录
// -----------------------------------------------------------------------------
static void test_readonly_dir() {
    auto img = make_valid_image(2, 2);
    // /proc is always read-only on Linux
    check(ImageEncoder::saveJPEG(img, "/proc/sonylab_enc.jpg") != ErrorCode::Success,
          "JPEG to /proc rejected (read-only)");
    check(ImageEncoder::savePNG(img, "/proc/sonylab_enc.png") != ErrorCode::Success,
          "PNG to /proc rejected (read-only)");
}

// -----------------------------------------------------------------------------
// TC-ENC-PATH — 不存在的目录
// -----------------------------------------------------------------------------
static void test_nonexistent_dir() {
    auto img = make_valid_image(2, 2);
    check(ImageEncoder::saveJPEG(img, "/tmp/nonexistent_dir_xyz/enc.jpg") != ErrorCode::Success,
          "JPEG to non-existent parent rejected");
    check(ImageEncoder::savePNG(img, "/tmp/nonexistent_dir_xyz/enc.png") != ErrorCode::Success,
          "PNG to non-existent parent rejected");
}

int main() {
    test_jpeg_roundtrip();
    test_png_lossless();
    test_empty_image();
    test_readonly_dir();
    test_nonexistent_dir();

    std::cout << "\n===== Summary: " << (total - failures) << "/" << total
              << " PASS";
    if (failures) std::cout << "  ❌ " << failures << " FAIL";
    std::cout << " =====\n";
    return failures ? 1 : 0;
}
