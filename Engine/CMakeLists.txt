add_library(engine)

target_sources(engine
    PRIVATE
        engine.cpp
)

target_include_directories(engine
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(engine PUBLIC engine_deps)