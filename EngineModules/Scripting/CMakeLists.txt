add_library(sle_scripting STATIC
    src/ScriptEngine.cpp
)

target_include_directories(sle_scripting
    PUBLIC
        include
)

target_link_libraries(sle_scripting
    PUBLIC
        sle::core
        sle::scene
        sle::resources
        lua::lua
)

add_library(sle::scripting ALIAS sle_scripting)
