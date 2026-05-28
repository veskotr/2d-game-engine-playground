add_library(sle_test_harness STATIC
    src/FrameStepper.cpp
    src/LogCapture.cpp
    src/SceneBootstrap.cpp
)

target_include_directories(sle_test_harness
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_features(sle_test_harness PUBLIC cxx_std_20)
