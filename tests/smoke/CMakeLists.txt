add_executable(smoke_harness_sentinel
    smoke_harness_sentinel.cpp
)

target_link_libraries(smoke_harness_sentinel
    PRIVATE
        sle_test_harness
)

add_test(
    NAME smoke.harness_sentinel
    COMMAND smoke_harness_sentinel
)

set_tests_properties(smoke.harness_sentinel PROPERTIES
    LABELS "smoke;harness"
)

add_executable(smoke_runtime_sentinel
    smoke_runtime_sentinel.cpp
)

target_link_libraries(smoke_runtime_sentinel
    PRIVATE
        sle_engine
        sle_test_harness
)

add_test(
    NAME smoke.runtime_sentinel
    COMMAND smoke_runtime_sentinel
)

set_tests_properties(smoke.runtime_sentinel PROPERTIES
    LABELS "smoke;runtime"
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)
