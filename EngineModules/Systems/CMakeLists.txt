add_library(sle_engine STATIC
    src/ScriptApiImpl.cpp
    src/SceneManager.cpp
    src/Runtime.cpp
    src/AnimationSystem.cpp
    src/TransformSystem.cpp
    src/ScriptSystem.cpp
    src/StateMachineSystem.cpp
    src/PhysicsSystem.cpp
    src/RenderSystem.cpp
)

target_include_directories(sle_engine
    PUBLIC
        include
)

target_link_libraries(sle_engine
    PUBLIC
        sle::core
        sle::platform
        sle::renderer
        sle::resources
        sle::scene
        sle::events
        sle::physics
        sle::scripting
        sle::ui
        glm::glm
)

add_library(sle::engine ALIAS sle_engine)
