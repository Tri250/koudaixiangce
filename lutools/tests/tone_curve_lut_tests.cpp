#include "sony2fuji/ffi/sony2fuji_c.h"
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <vector>

namespace {
void check(bool condition, const char* name) {
    std::cout << (condition ? "PASS " : "FAIL ") << name << std::endl;
    if (!condition) std::exit(1);
}
struct Session {
    sony2fuji_session* value = nullptr;
    Session() { check(sony2fuji_session_create(&value) == SONY2FUJI_STATUS_OK, "create session"); }
    ~Session() { sony2fuji_session_destroy(value); }
};
constexpr uint32_t kWidth = 16, kHeight = 16;

std::vector<uint8_t> ramp() {
    std::vector<uint8_t> pixels(size_t(kWidth) * kHeight * 4);
    for (uint32_t y = 0; y < kHeight; ++y)
        for (uint32_t x = 0; x < kWidth; ++x) {
            const uint8_t v = static_cast<uint8_t>((y * kWidth + x) % 256);
            const size_t i = (size_t(y) * kWidth + x) * 4;
            pixels[i] = pixels[i + 1] = pixels[i + 2] = v;
            pixels[i + 3] = 255;
        }
    return pixels;
}

sony2fuji_request makeRequest(const std::vector<uint8_t>& pixels) {
    sony2fuji_request request{};
    request.version = SONY2FUJI_REQUEST_VERSION;
    request.struct_size = sizeof(request);
    request.input_type = SONY2FUJI_INPUT_BUFFER;
    request.input_pixels = pixels.data();
    request.input_width = kWidth;
    request.input_height = kHeight;
    request.input_pixel_format = SONY2FUJI_PIXEL_RGBA8;
    request.input_color_space = SONY2FUJI_COLOR_SRGB;
    request.input_is_linear = 0;
    request.brightness = request.contrast = request.saturation = 1;
    request.wb_mode = SONY2FUJI_WB_CAMERA;
    request.temperature = 6500;
    request.intent = SONY2FUJI_INTENT_PREVIEW;
    request.size_mode = SONY2FUJI_SIZE_NATIVE;
    request.output_target = SONY2FUJI_TARGET_BUFFER;
    request.output_format = SONY2FUJI_OUTPUT_RGBA8;
    return request;
}

std::vector<uint8_t> render(Session& session, const sony2fuji_request& request) {
    sony2fuji_buffer buffer{};
    check(sony2fuji_process(session.value, &request, &buffer) == SONY2FUJI_STATUS_OK, "render buffer");
    auto* bytes = static_cast<uint8_t*>(buffer.data);
    std::vector<uint8_t> output(bytes, bytes + buffer.size_bytes);
    sony2fuji_release_buffer(&buffer);
    return output;
}
}

int main() {
    const auto pixels = ramp();
    auto request = makeRequest(pixels);
    Session session;

    // Validation contract.
    check(sony2fuji_session_set_tone_curve_lut(nullptr, nullptr) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
        "null session rejected");
    float lut[256];
    for (int i = 0; i < 256; ++i) lut[i] = i / 255.0f;
    check(sony2fuji_session_set_tone_curve_lut(session.value, lut) == SONY2FUJI_STATUS_OK,
        "identity LUT accepted");
    const float bad[] = {NAN};
    check(sony2fuji_session_set_tone_curve_lut(session.value, bad) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
        "NaN LUT rejected");
    const float overflow[] = {1.5f};
    check(sony2fuji_session_set_tone_curve_lut(session.value, overflow) == SONY2FUJI_STATUS_INVALID_ARGUMENT,
        "out-of-range LUT rejected");

    // Identity LUT keeps the neutral pipeline byte-exact.
    const auto neutral = render(session, request);
    check(neutral.size() == pixels.size(), "identity LUT output size");
    int maximum = 0;
    for (size_t i = 0; i < neutral.size(); i += 4)
        maximum = std::max(maximum, std::abs(int(neutral[i]) - int(pixels[i])));
    check(maximum <= 1, "identity LUT is a neutral bypass");

    // A brightening LUT maps every grey level through the same 256-entry ramp.
    for (int i = 0; i < 256; ++i) lut[i] = std::pow(i / 255.0f, 1.0f / 2.2f);
    check(sony2fuji_session_set_tone_curve_lut(session.value, lut) == SONY2FUJI_STATUS_OK,
        "gamma LUT accepted");
    const auto lifted = render(session, request);
    maximum = 0;
    for (size_t i = 0; i < lifted.size(); i += 4) {
        const float expected = lut[std::min(255, int(pixels[i]))] * 255.0f;
        maximum = std::max(maximum, std::abs(int(lifted[i]) - int(std::lround(expected))));
    }
    check(maximum <= 1, "gamma LUT output matches per-bin sampling");

    // While a LUT is active the legacy tone_curve scalar is ignored.
    request.tone_curve = 0.5f;
    const auto stacked = render(session, request);
    check(stacked == lifted, "active LUT gates the legacy tone_curve scalar");
    request.tone_curve = 0.0f;

    // Disabling restores scalar behaviour.
    check(sony2fuji_session_set_tone_curve_lut(session.value, nullptr) == SONY2FUJI_STATUS_OK,
        "nullptr disables the LUT");
    const auto scalarOff = render(session, request);
    maximum = 0;
    for (size_t i = 0; i < scalarOff.size(); i += 4)
        maximum = std::max(maximum, std::abs(int(scalarOff[i]) - int(neutral[i])));
    check(maximum <= 1, "disabled LUT returns to the neutral pipeline");
    request.tone_curve = 0.5f;
    const auto scalarOn = render(session, request);
    check(scalarOn != neutral, "legacy tone_curve scalar applies after disabling");
    return 0;
}
