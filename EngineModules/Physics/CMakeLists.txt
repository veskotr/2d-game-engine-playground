add_library(sle_physics STATIC
    src/PhysicsWorld.cpp
    src/ContactListener.cpp
)

target_include_directories(sle_physics
    PUBLIC
        include
)

target_link_libraries(sle_physics
    PUBLIC
        sle::core
        sle::scene
        box2d
    PRIVATE
        glm::glm
)

add_library(sle::physics ALIAS sle_physics)
