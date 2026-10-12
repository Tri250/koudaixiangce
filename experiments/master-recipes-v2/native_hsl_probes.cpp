#include "photo_color.h"
#include <iomanip>
#include <iostream>
#include <random>

int main() {
    sony2fuji::HslOptions controls;
    controls.hue = {10, -5, 0, 8, -10, 5, 2, -7};
    controls.saturation = {-15, 10, 5, -8, 7, -9, 4, 12};
    controls.lightness = {1, -2, 0, 1, 2, -1, 0, 1};
    std::mt19937 generator(42);
    std::uniform_real_distribution<float> value(-0.2f, 1.2f);
    std::cout << std::setprecision(9);
    for (int i = 0; i < 2048; ++i) {
        const sony2fuji::RGB input(value(generator), value(generator), value(generator));
        const auto output = sony2fuji::applyHsl(input, controls);
        std::cout << input.r << ',' << input.g << ',' << input.b << ','
                  << output.r << ',' << output.g << ',' << output.b << '\n';
    }
}
