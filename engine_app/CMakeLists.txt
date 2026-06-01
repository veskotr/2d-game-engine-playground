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
