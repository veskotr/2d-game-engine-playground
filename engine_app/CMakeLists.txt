add_executable(engine_app
    main.cpp
)

if(MSVC)
    set_property(
        TARGET engine_app
        PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL"
    )
endif()

target_link_libraries(engine_app
    PRIVATE
        sle::engine
        nlohmann_json::nlohmann_json
)

set_target_properties(engine_app PROPERTIES
    FOLDER apps
)

# Engine-owned resources accompany the executable; project data stays external.
add_custom_command(TARGET engine_app POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory
        "${CMAKE_SOURCE_DIR}/assets/shaders" "$<TARGET_FILE_DIR:engine_app>/assets/shaders"
    COMMAND ${CMAKE_COMMAND} -E copy_directory
        "${CMAKE_SOURCE_DIR}/assets/ui" "$<TARGET_FILE_DIR:engine_app>/assets/ui"
    COMMAND ${CMAKE_COMMAND} -E copy_directory
        "${CMAKE_SOURCE_DIR}/assets/fonts" "$<TARGET_FILE_DIR:engine_app>/assets/fonts"
)

# A separate build artifact: authoring/export never invokes the compiler implicitly.
if(WIN32 AND MSVC AND CMAKE_SIZEOF_VOID_P EQUAL 8)
    set(CMAKE_INSTALL_SYSTEM_RUNTIME_LIBS_SKIP TRUE)
    set(CMAKE_INSTALL_DEBUG_LIBRARIES FALSE)
    include(InstallRequiredSystemLibraries)
    get_filename_component(sle_compiler_directory "${CMAKE_CXX_COMPILER}" DIRECTORY)
    configure_file("${CMAKE_SOURCE_DIR}/cmake/RuntimeBundleConfig.cmake.in"
        "${CMAKE_CURRENT_BINARY_DIR}/RuntimeBundleConfig.cmake" @ONLY)
    add_custom_target(sle_runtime_bundle
        COMMAND ${CMAKE_COMMAND}
            -DENGINE_APP=$<TARGET_FILE:engine_app>
            -DCONFIGURATION=$<CONFIG>
            -DOUTPUT_DIRECTORY=${CMAKE_BINARY_DIR}/runtime/$<CONFIG>
            -P ${CMAKE_CURRENT_BINARY_DIR}/RuntimeBundleConfig.cmake
        DEPENDS engine_app
        VERBATIM
    )
endif()
