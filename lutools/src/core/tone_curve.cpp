#include "core/tone_curve.h"

#include <algorithm>
#include <cmath>

namespace sony2fuji {

std::shared_ptr<ToneCurveMap> ToneCurveMap::build(const std::vector<CurvePoint>& points) {
    const size_t count = points.size();
    if (count < 2 || count > kMaxPoints) {
        return nullptr;
    }
    for (size_t i = 0; i < count; ++i) {
        const CurvePoint& point = points[i];
        if (!std::isfinite(point.x) || !std::isfinite(point.y)) {
            return nullptr;
        }
        if (point.x < 0.0f || point.x > 1.0f || point.y < 0.0f || point.y > 1.0f) {
            return nullptr;
        }
        if (i > 0 && !(point.x > points[i - 1].x)) {
            return nullptr;
        }
    }

    auto map = std::shared_ptr<ToneCurveMap>(new ToneCurveMap());
    map->xs.reserve(count);
    map->ys.reserve(count);
    for (const CurvePoint& point : points) {
        map->xs.push_back(point.x);
        map->ys.push_back(point.y);
    }

    // Fritsch–Carlson tangents: the weighted harmonic mean of adjacent secant
    // slopes keeps each monotone interval monotone; sign changes get zero
    // tangent so the curve never overshoots the anchor data range.
    const size_t segments = count - 1;
    std::vector<float> widths(segments);
    std::vector<float> slopes(segments);
    for (size_t i = 0; i < segments; ++i) {
        widths[i] = map->xs[i + 1] - map->xs[i];
        slopes[i] = (map->ys[i + 1] - map->ys[i]) / widths[i];
    }
    map->tangents.assign(count, 0.0f);
    map->tangents.front() = slopes.front();
    map->tangents.back() = slopes.back();
    for (size_t i = 1; i < segments; ++i) {
        const float left = slopes[i - 1];
        const float right = slopes[i];
        if (left * right <= 0.0f) {
            continue; // Local extremum or plateau: zero tangent.
        }
        const float w1 = 2.0f * widths[i] + widths[i - 1];
        const float w2 = widths[i] + 2.0f * widths[i - 1];
        map->tangents[i] = (w1 + w2) / (w1 / left + w2 / right);
    }
    return map;
}

float ToneCurveMap::evaluate(float x) const {
    if (x <= xs.front()) {
        return ys.front();
    }
    if (x >= xs.back()) {
        return ys.back();
    }
    const size_t i = static_cast<size_t>(
        std::upper_bound(xs.begin(), xs.end(), x) - xs.begin()) - 1;
    const float width = xs[i + 1] - xs[i];
    const float t = (x - xs[i]) / width;
    const float t2 = t * t;
    const float t3 = t2 * t;
    const float h00 = 2.0f * t3 - 3.0f * t2 + 1.0f;
    const float h10 = t3 - 2.0f * t2 + t;
    const float h01 = -2.0f * t3 + 3.0f * t2;
    const float h11 = t3 - t2;
    return h00 * ys[i] + h10 * width * tangents[i] +
           h01 * ys[i + 1] + h11 * width * tangents[i + 1];
}

bool ToneCurveMap::isIdentity() const {
    return xs.size() == 2 && xs[0] == 0.0f && ys[0] == 0.0f &&
           xs[1] == 1.0f && ys[1] == 1.0f;
}

} // namespace sony2fuji
