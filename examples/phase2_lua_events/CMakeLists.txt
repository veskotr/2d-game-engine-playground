add_executable(example_phase2_lua_events
    main.cpp
)

target_link_libraries(example_phase2_lua_events
    PRIVATE
        sle::events
        sle::scripting
        lua::lua
)

add_custom_command(TARGET example_phase2_lua_events POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory
    ${CMAKE_SOURCE_DIR}/examples/phase2_lua_events/assets
    $<TARGET_FILE_DIR:example_phase2_lua_events>/assets
)
