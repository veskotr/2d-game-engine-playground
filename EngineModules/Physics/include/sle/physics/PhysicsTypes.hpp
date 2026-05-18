#pragma once
#include <cstdint>

namespace sle::physics {

// Physics world configuration constants
constexpr float DEFAULT_GRAVITY_Y = -9.81f;
constexpr float DEFAULT_FIXED_TIMESTEP = 1.0f / 60.0f;  // 60 Hz

// Box2D iterations (affects simulation quality vs speed)
constexpr int32_t DEFAULT_VELOCITY_ITERATIONS = 8;
constexpr int32_t DEFAULT_POSITION_ITERATIONS = 3;

} // namespace sle::physics
