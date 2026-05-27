add_executable(example_phase3_advanced_events
    main.cpp
)

target_link_libraries(example_phase3_advanced_events
    PRIVATE
        sle::events
)
