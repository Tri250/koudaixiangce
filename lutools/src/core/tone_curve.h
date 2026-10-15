#pragma once

#include <cstdint>
#include <memory>
#include <vector>

namespace sony2fuji {

struct CurvePoint {
    float x; // 0..1, strictly increasing across the anchor set.
    float y; // 0..1.
};

// User tone curve over display-sRGB values, interpolated with the
// Fritsch–Carlson monotone cubic Hermite so a monotone anchor set can never
// overshoot its data range. Construction rejects non-finite values, anchors
// outside [0,1] and x sets that are not strictly increasing.
class ToneCurveMap {
public:
    static constexpr uint32_t kMaxPoints = 64;

    // nullptr when the anchor set is invalid; identity anchors are buildable.
    static std::shared_ptr<ToneCurveMap> build(const std::vector<CurvePoint>& points);

    // Clamps to the first/last anchor value outside the anchor domain.
    float evaluate(float x) const;

    // Exactly the linear identity [(0,0),(1,1)]: callers store nothing.
    bool isIdentity() const;

private:
    ToneCurveMap() = default;
    std::vector<float> xs;
    std::vector<float> ys;
    std::vector<float> tangents;
};

} // namespace sony2fuji
