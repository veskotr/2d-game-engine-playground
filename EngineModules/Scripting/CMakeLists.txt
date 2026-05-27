add_library(sle_scripting STATIC
    src/LuaBindings.cpp
    src/LuaBindingsEngine.cpp
    src/LuaBindingsPhysics.cpp
    src/LuaBindingsInput.cpp
    src/LuaBindingsCamera.cpp
    src/LuaBindingsEvents.cpp
    src/LuaBindingsConstants.cpp
    src/ScriptEngine.cpp
)

target_include_directories(sle_scripting
    PUBLIC
        include
)

target_link_libraries(sle_scripting
    PUBLIC
        sle::core
        sle::platform
        sle::scene
        sle::resources
        lua::lua
)

add_library(sle::scripting ALIAS sle_scripting)

find_package(Python3 COMPONENTS Interpreter QUIET)

if(Python3_Interpreter_FOUND)
    set(LUA_API_STUB_OUTPUT ${CMAKE_SOURCE_DIR}/assets/scripts/engine_api.lua)
    set(LUA_BINDINGS_SOURCES
        ${CMAKE_CURRENT_SOURCE_DIR}/src/LuaBindings.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/LuaBindingsEngine.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/LuaBindingsPhysics.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/LuaBindingsInput.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/LuaBindingsCamera.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/LuaBindingsEvents.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/LuaBindingsConstants.cpp
    )

    add_custom_command(
        OUTPUT ${LUA_API_STUB_OUTPUT}
        COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/tools/generate_lua_api_stub.py
            --bindings-dir ${CMAKE_CURRENT_SOURCE_DIR}/src
            --output ${LUA_API_STUB_OUTPUT}
        DEPENDS
            ${CMAKE_CURRENT_SOURCE_DIR}/tools/generate_lua_api_stub.py
            ${LUA_BINDINGS_SOURCES}
        COMMENT "Generating Lua API IntelliSense stub"
        VERBATIM
    )

    add_custom_target(generate_lua_api_stub ALL DEPENDS ${LUA_API_STUB_OUTPUT})
    add_dependencies(sle_scripting generate_lua_api_stub)
else()
    message(WARNING "Python3 not found: skipping Lua API stub generation")
endif()
