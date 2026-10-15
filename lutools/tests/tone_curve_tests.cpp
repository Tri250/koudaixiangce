#include "core/tone_curve.h"
#ifndef TONE_CURVE_ENGINE_ONLY
#include "sony2fuji/ffi/sony2fuji_c.h"
#endif
#include <cmath>
#include <cstdint>
#include <iostream>
#include <vector>

using namespace sony2fuji;

int failures = 0;

void check(bool ok, const char* name) {
    std::cout << (ok ? "PASS " : "FAIL ") << name << '\n';
    if (!ok) ++failures;
}

bool near(float a, float b, float tolerance = 1e-5f) { return std::abs(a - b) < tolerance; }

std::vector<CurvePoint> curve(std::initializer_list<CurvePoint> points) {
    return std::vector<CurvePoint>(points.begin(), points.end());
}

void engineTests() {
    const auto identity = ToneCurveMap::build(curve({{0.0f, 0.0f}, {1.0f, 1.0f}}));
    check(identity && identity->isIdentity(), "Identity anchors build and detect as identity");
    bool identityPass = identity != nullptr;
    for (int i = 0; i <= 16 && identityPass; ++i) {
        const float x = i / 16.0f;
        identityPass = near(identity->evaluate(x), x);
    }
    check(identityPass, "Identity curve evaluates to the input value");

    check(!ToneCurveMap::build(curve({{0.0f, 0.0f}})), "Single anchor is rejected");
    std::vector<CurvePoint> tooMany;
    for (int i = 0; i <= 64; ++i) tooMany.push_back({i / 64.0f, i / 64.0f});
    check(tooMany.size() == 65 && !ToneCurveMap::build(tooMany), "More than 64 anchors are rejected");
    check(!ToneCurveMap::build(curve({{NAN, 0.0f}, {1.0f, 1.0f}})), "Non-finite x is rejected");
    check(!ToneCurveMap::build(curve({{0.0f, 0.0f}, {1.0f, INFINITY}})), "Non-finite y is rejected");
    check(!ToneCurveMap::build(curve({{-0.1f, 0.0f}, {1.0f, 1.0f}})), "x below zero is rejected");
    check(!ToneCurveMap::build(curve({{0.0f, 0.0f}, {1.1f, 1.0f}})), "x above one is rejected");
    check(!ToneCurveMap::build(curve({{0.0f, -0.1f}, {1.0f, 1.0f}})), "y below zero is rejected");
    check(!ToneCurveMap::build(curve({{0.0f, 0.0f}, {1.0f, 1.5f}})), "y above one is rejected");
    check(!ToneCurveMap::build(curve({{0.0f, 0.0f}, {0.5f, 0.5f}, {0.5f, 1.0f}})), "Duplicate x is rejected");
    check(!ToneCurveMap::build(curve({{0.0f, 0.0f}, {0.75f, 0.5f}, {0.5f, 1.0f}})), "Decreasing x is rejected");

    const auto sCurve = ToneCurveMap::build(
        curve({{0.0f, 0.0f}, {0.25f, 0.1f}, {0.5f, 0.8f}, {0.75f, 0.9f}, {1.0f, 1.0f}}));
    bool monotone = sCurve != nullptr;
    float previous = -1.0f;
    for (int i = 0; i <= 512 && monotone; ++i) {
        const float value = sCurve->evaluate(i / 512.0f);
        monotone = value >= previous - 1e-6f;
        previous = value;
    }
    check(monotone, "Monotone anchors produce a non-decreasing curve");

    const auto steep = ToneCurveMap::build(
        curve({{0.0f, 0.0f}, {0.4f, 0.9f}, {0.6f, 0.95f}, {1.0f, 1.0f}}));
    bool bounded = steep != nullptr;
    for (int i = 0; i <= 512 && bounded; ++i) {
        const float value = steep->evaluate(i / 512.0f);
        bounded = value >= -1e-6f && value <= 1.0f + 1e-6f;
    }
    check(bounded, "Steep monotone anchors never overshoot the data range");

    const auto plateau = ToneCurveMap::build(
        curve({{0.0f, 0.0f}, {0.3f, 0.5f}, {0.7f, 0.5f}, {1.0f, 1.0f}}));
    check(plateau && near(plateau->evaluate(0.5f), 0.5f, 1e-6f),
          "Plateau anchors hold the plateau value between them");

    const auto linear = ToneCurveMap::build(curve({{0.0f, 0.0f}, {1.0f, 0.5f}}));
    check(linear && near(linear->evaluate(-0.25f), 0.0f) && near(linear->evaluate(1.25f), 0.5f),
          "Evaluation clamps to the endpoint values outside the anchors");

    const auto inverted = ToneCurveMap::build(curve({{0.0f, 1.0f}, {1.0f, 0.0f}}));
    bool decreasing = inverted != nullptr;
    float last = 2.0f;
    for (int i = 0; i <= 512 && decreasing; ++i) {
        const float value = inverted->evaluate(i / 512.0f);
        decreasing = value <= last + 1e-6f;
        last = value;
    }
    check(decreasing, "Inverted anchors produce a non-increasing curve");

    const auto subdomain = ToneCurveMap::build(curve({{0.2f, 0.1f}, {0.8f, 0.9f}}));
    check(subdomain && near(subdomain->evaluate(0.0f), 0.1f) && near(subdomain->evaluate(1.0f), 0.9f),
          "Anchors inside [0,1] clamp outside their domain");
}

#ifndef TONE_CURVE_ENGINE_ONLY
sony2fuji_request grayRequest(const unsigned char* pixels) {
    sony2fuji_request r{};
    r.version = SONY2FUJI_REQUEST_VERSION;
    r.struct_size = sizeof(r);
    r.input_type = SONY2FUJI_INPUT_BUFFER;
    r.input_pixels = pixels;
    r.input_width = r.input_height = 1;
    r.input_pixel_format = SONY2FUJI_PIXEL_RGB8;
    r.input_color_space = SONY2FUJI_COLOR_SRGB;
    r.input_is_linear = 1;
    r.output_target = SONY2FUJI_TARGET_BUFFER;
    r.output_format = SONY2FUJI_OUTPUT_RGB8;
    r.size_mode = SONY2FUJI_SIZE_EXACT;
    r.target_width = r.target_height = 1;
    r.brightness = r.contrast = r.saturation = 1;
    r.temperature = 6500;
    r.wb_mode = SONY2FUJI_WB_CAMERA;
    return r;
}

int renderGray(sony2fuji_session* session, const sony2fuji_request& request) {
    sony2fuji_buffer out{};
    if (sony2fuji_process(session, &request, &out) != SONY2FUJI_STATUS_OK || !out.data) {
        sony2fuji_release_buffer(&out);
        return -1;
    }
    const int value = static_cast<const unsigned char*>(out.data)[0];
    sony2fuji_release_buffer(&out);
    return value;
}

void ffiTests() {
    sony2fuji_session* session = nullptr;
    if (sony2fuji_session_create(&session) != SONY2FUJI_STATUS_OK || !session) {
        check(false, "FFI session creates for tone curve tests");
        return;
    }
    sony2fuji_gpu_config config{SONY2FUJI_GPU_CONFIG_VERSION, sizeof(sony2fuji_gpu_config), SONY2FUJI_GPU_OFF};
    sony2fuji_session_set_gpu_config(session, &config);

    const unsigned char gray[3] = {46, 46, 46};
    const auto request = grayRequest(gray);

    check(sony2fuji_session_set_tone_curve(session, nullptr, 0) == SONY2FUJI_STATUS_OK,
          "Null curve clears without error");
    const int baseline = renderGray(session, request);
    check(baseline >= 0, "Baseline CPU render succeeds without a curve");

    const sony2fuji_curve_point darkening[] = {{0.0f, 0.0f}, {1.0f, 0.5f}};
    check(sony2fuji_session_set_tone_curve(session, darkening, 2) == SONY2FUJI_STATUS_OK,
          "Valid two-anchor curve is accepted");
    const int darkened = renderGray(session, request);
    check(darkened >= 0 && darkened < baseline - 20,
          "Active curve darkens the rendered output");

    const sony2fuji_curve_point identity[] = {{0.0f, 0.0f}, {1.0f, 1.0f}};
    check(sony2fuji_session_set_tone_curve(session, identity, 2) == SONY2FUJI_STATUS_OK,
          "Identity curve is accepted");
    check(renderGray(session, request) == baseline,
          "Identity curve renders bit-identical to no curve");

    std::vector<sony2fuji_curve_point> tooMany;
    for (int i = 0; i <= 64; ++i) tooMany.push_back({i / 64.0f, i / 64.0f});
    check(sony2fuji_session_set_tone_curve(session, tooMany.data(), 65) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
          "More than 64 anchors return INVALID_ARGUMENT");

    const sony2fuji_curve_point duplicateX[] = {{0.0f, 0.0f}, {0.5f, 0.25f}, {0.5f, 0.75f}, {1.0f, 1.0f}};
    check(sony2fuji_session_set_tone_curve(session, duplicateX, 4) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
          "Duplicate x anchors return INVALID_ARGUMENT");
    const sony2fuji_curve_point outOfRange[] = {{0.0f, 0.0f}, {1.0f, 1.5f}};
    check(sony2fuji_session_set_tone_curve(session, outOfRange, 2) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
          "y outside [0,1] returns INVALID_ARGUMENT");
    const sony2fuji_curve_point nonFinite[] = {{0.0f, NAN}, {1.0f, 1.0f}};
    check(sony2fuji_session_set_tone_curve(session, nonFinite, 2) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
          "Non-finite anchors return INVALID_ARGUMENT");

    // Rejected sets leave the previously accepted curve untouched.
    check(sony2fuji_session_set_tone_curve(session, darkening, 2) == SONY2FUJI_STATUS_OK,
          "Darkening curve re-applies after rejected sets");
    check(renderGray(session, request) == darkened,
          "Rejected sets do not disturb the active curve");

    config.mode = SONY2FUJI_GPU_FORCE;
    sony2fuji_session_set_gpu_config(session, &config);
    sony2fuji_buffer forced{};
    check(sony2fuji_process(session, &request, &forced) == SONY2FUJI_STATUS_PROCESSING_ERROR,
          "Force mode with an active curve reports an error instead of skipping it");
    sony2fuji_release_buffer(&forced);
    config.mode = SONY2FUJI_GPU_OFF;
    sony2fuji_session_set_gpu_config(session, &config);

    check(sony2fuji_session_set_tone_curve(session, nullptr, 0) == SONY2FUJI_STATUS_OK,
          "Clearing the curve succeeds");
    check(renderGray(session, request) == baseline,
          "Cleared curve restores the baseline render");

    sony2fuji_session_destroy(session);
}
#endif // TONE_CURVE_ENGINE_ONLY

#ifndef TONE_CURVE_ENGINE_ONLY
void cropAndPickerTests() {
    sony2fuji_session* session = nullptr;
    if (sony2fuji_session_create(&session) != SONY2FUJI_STATUS_OK || !session) {
        check(false, "FFI session creates for crop tests");
        return;
    }
    sony2fuji_gpu_config config{SONY2FUJI_GPU_CONFIG_VERSION, sizeof(sony2fuji_gpu_config), SONY2FUJI_GPU_OFF};
    sony2fuji_session_set_gpu_config(session, &config);

    check(sony2fuji_session_set_crop(session, nullptr) == SONY2FUJI_STATUS_OK,
          "Null crop clears without error");
    const sony2fuji_crop_config half = {SONY2FUJI_CROP_CONFIG_VERSION, sizeof(sony2fuji_crop_config),
        0.0f, 0.0f, 0.5f, 1.0f, 0.0f};
    check(sony2fuji_session_set_crop(session, &half) == SONY2FUJI_STATUS_OK,
          "Valid half-width crop is accepted");
    const sony2fuji_crop_config badSize = {SONY2FUJI_CROP_CONFIG_VERSION, sizeof(sony2fuji_crop_config),
        0.0f, 0.0f, 0.0f, 1.0f, 0.0f};
    check(sony2fuji_session_set_crop(session, &badSize) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
          "Zero-width crop is rejected");
    const sony2fuji_crop_config overflow = {SONY2FUJI_CROP_CONFIG_VERSION, sizeof(sony2fuji_crop_config),
        0.5f, 0.0f, 0.6f, 1.0f, 0.0f};
    check(sony2fuji_session_set_crop(session, &overflow) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
          "Crop rectangle wider than the frame is rejected");
    const sony2fuji_crop_config badAngle = {SONY2FUJI_CROP_CONFIG_VERSION, sizeof(sony2fuji_crop_config),
        0.0f, 0.0f, 0.5f, 1.0f, 60.0f};
    check(sony2fuji_session_set_crop(session, &badAngle) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
          "Leveling beyond 45 degrees is rejected");
    // Rejected sets leave the previously accepted crop active; clear it before
    // the render comparisons below.
    check(sony2fuji_session_set_crop(session, nullptr) == SONY2FUJI_STATUS_OK,
          "Crop clears after rejected sets");

    // A red/blue 2x1 buffer rendered at 2x1: cropping to the left half turns
    // the right output pixel red as well (stretched), while the full frame
    // keeps blue there.
    const unsigned char rb[6] = {255, 0, 0, 0, 0, 255};
    auto request = grayRequest(rb);
    request.input_width = 2;
    request.input_height = 1;
    request.target_width = 2;
    request.target_height = 1;
    const unsigned char* probe = nullptr;
    sony2fuji_buffer probeBuffer{};
    auto renderRedAt = [&](sony2fuji_session* s, const sony2fuji_request& req, int index) {
        if (sony2fuji_process(s, &req, &probeBuffer) != SONY2FUJI_STATUS_OK || !probeBuffer.data) {
            sony2fuji_release_buffer(&probeBuffer);
            return -1;
        }
        probe = static_cast<const unsigned char*>(probeBuffer.data);
        const int value = probe[index * 3];
        sony2fuji_release_buffer(&probeBuffer);
        return value;
    };
    const int fullRightBlue = renderRedAt(session, request, 1);
    check(fullRightBlue >= 0 && fullRightBlue < 100,
          "Uncropped right half renders blue");
    check(sony2fuji_session_set_crop(session, &half) == SONY2FUJI_STATUS_OK,
          "Half-width crop applies to a buffer render");
    const int croppedRightRed = renderRedAt(session, request, 1);
    check(croppedRightRed > 200,
          "Cropped output contains only the left half's red");

    check(sony2fuji_session_set_crop(session, nullptr) == SONY2FUJI_STATUS_OK,
          "Clearing the crop succeeds");
    check(renderRedAt(session, request, 1) == fullRightBlue,
          "Cleared crop restores the uncropped render bit-exactly");

    float temperature = 0, tint = 0;
    check(sony2fuji_session_pick_white_balance(session, 0.5f, 0.5f, &temperature, &tint)
              == SONY2FUJI_STATUS_UNSUPPORTED,
          "Eyedropper reports UNSUPPORTED without a decoded RAW");
    check(sony2fuji_session_pick_white_balance(session, 1.5f, 0.5f, &temperature, &tint)
              == SONY2FUJI_STATUS_INVALID_ARGUMENT,
          "Eyedropper rejects out-of-range coordinates");
    check(sony2fuji_session_pick_white_balance(session, 0.5f, 0.5f, nullptr, &tint)
              == SONY2FUJI_STATUS_INVALID_ARGUMENT,
          "Eyedropper rejects null outputs");

    sony2fuji_session_destroy(session);
}

int main() {
    std::cout << std::unitbuf;
    engineTests();
    ffiTests();
    cropAndPickerTests();
    std::cout << (failures == 0 ? "ALL PASS" : "FAILURES") << ": " << failures << '\n';
    return failures == 0 ? 0 : 1;
}
#endif // TONE_CURVE_ENGINE_ONLY
