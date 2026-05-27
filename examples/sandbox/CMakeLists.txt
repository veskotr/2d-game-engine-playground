add_executable(example_sandbox
    main.cpp
)

target_link_libraries(example_sandbox PRIVATE sle::engine)

# Sandbox example is fully self-contained under examples/sandbox/assets.
add_custom_command(TARGET example_sandbox POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory
        ${CMAKE_SOURCE_DIR}/examples/sandbox/assets
        $<TARGET_FILE_DIR:example_sandbox>/assets
)
