add_library(sle_events INTERFACE)

target_include_directories(sle_events
    INTERFACE
        include
)

target_link_libraries(sle_events
    INTERFACE
        sle::core
)

add_library(sle::events ALIAS sle_events)
