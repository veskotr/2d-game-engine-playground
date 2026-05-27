add_library(sle_ui STATIC
    src/UIBindingContext.cpp
    src/UIDocument.cpp
    src/UILayoutParser.cpp
    src/UIResources.cpp
    src/UISystem.cpp
)

target_include_directories(sle_ui
    PUBLIC
    include
)

target_link_libraries(sle_ui PUBLIC
    sle::events
    sle::platform
    sle::renderer
    sle::resources
    sle::scene
    sle::scripting
    stb_truetype
    pugixml
)

add_library(sle::ui ALIAS sle_ui)
