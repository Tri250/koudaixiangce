#pragma once
#include <libraw/libraw_types.h>
#include <array>

namespace sony2fuji {
struct WhitePoint { double x, y; };
struct TemperatureTint { double temperature, tint; };
WhitePoint temperatureWhitePoint(double temperature, double tint);
TemperatureTint whitePointTemperature(WhitePoint white);

class CameraWhiteBalance {
public:
    explicit CameraWhiteBalance(const libraw_data_t& raw);
    /// True when a camera color matrix is available for temperature WB multipliers.
    bool available() const { return matrixValid_; }
    /// True when as-shot WB was successfully estimated from cam_mul metadata.
    /// Returns a D50 fallback (5500K, tint 0) when the camera didn't record WB multipliers.
    TemperatureTint asShot() const { return asShot_; }
    bool hasAsShot() const { return asShotValid_; }
    bool multipliers(double temperature, double tint, float output[4]) const;
private:
    using Matrix = std::array<std::array<double,3>,3>;
    Matrix matrix(double temperature) const;
    Matrix first_{}, second_{};
    double firstTemperature_ = 0, secondTemperature_ = 0;
    TemperatureTint asShot_{5500,0};
    bool matrixValid_ = false;
    bool asShotValid_ = false;
};
}
