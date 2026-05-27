add_executable(example_phase1_eventbus
    main.cpp
)

target_link_libraries(example_phase1_eventbus
    PRIVATE
        sle::events
)
