function(sle_add_integration_test targetName sourceFile labels)
    set(options)
    set(oneValueArgs WORKING_DIRECTORY)
    set(multiValueArgs LINK_LIBS)
    cmake_parse_arguments(SLE_IT "${options}" "${oneValueArgs}" "${multiValueArgs}" ${ARGN})

    add_executable(${targetName} ${sourceFile})

    if(MSVC)
        set_property(
            TARGET ${targetName}
            PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL"
        )
    endif()

    if(SLE_IT_LINK_LIBS)
        target_link_libraries(${targetName} PRIVATE ${SLE_IT_LINK_LIBS})
    endif()

    add_test(NAME ${targetName} COMMAND ${targetName})

    set(_allLabels "integration;${labels}")
    set_tests_properties(${targetName} PROPERTIES LABELS "${_allLabels}")

    if(SLE_IT_WORKING_DIRECTORY)
        set_tests_properties(${targetName} PROPERTIES WORKING_DIRECTORY "${SLE_IT_WORKING_DIRECTORY}")
    endif()

endfunction()

# Events
sle_add_integration_test(
    integration_events_happy_path
    events/integration_events_happy_path.cpp
    events
    LINK_LIBS sle::events sle::scene
)

sle_add_integration_test(
    integration_events_edge_path
    events/integration_events_edge_path.cpp
    events
    LINK_LIBS sle::events
)

sle_add_integration_test(
    integration_events_scene_teardown_path
    events/integration_events_scene_teardown_path.cpp
    events
    LINK_LIBS sle::events sle::scene
)

sle_add_integration_test(
    integration_events_scene_lifecycle_order_path
    events/integration_events_scene_lifecycle_order_path.cpp
    events
    LINK_LIBS sle::engine
)

sle_add_integration_test(
    integration_events_scene_switch_invalid_path
    events/integration_events_scene_switch_invalid_path.cpp
    events
    LINK_LIBS sle::engine
)

sle_add_integration_test(
    integration_events_subscription_persistence_path
    events/integration_events_subscription_persistence_path.cpp
    events
    LINK_LIBS sle::engine
)

sle_add_integration_test(
    integration_events_scene_switch_coalescing_path
    events/integration_events_scene_switch_coalescing_path.cpp
    events
    LINK_LIBS sle::engine
)

sle_add_integration_test(
    integration_events_physics_eventbus_swap_path
    events/integration_events_physics_eventbus_swap_path.cpp
    events
    LINK_LIBS sle::physics sle::events
)

# Physics
sle_add_integration_test(
    integration_physics_happy_path
    physics/integration_physics_happy_path.cpp
    physics
    LINK_LIBS sle::physics sle::events
)

sle_add_integration_test(
    integration_physics_edge_path
    physics/integration_physics_edge_path.cpp
    physics
    LINK_LIBS sle::physics
)

sle_add_integration_test(
    integration_physics_contact_deferred_dispatch_path
    physics/integration_physics_contact_deferred_dispatch_path.cpp
    physics
    LINK_LIBS sle::physics sle::events
)

sle_add_integration_test(
    integration_physics_zone_enter_exit_path
    physics/integration_physics_zone_enter_exit_path.cpp
    physics
    LINK_LIBS sle::physics sle::events
)

# Scripting
sle_add_integration_test(
    integration_scripting_happy_path
    scripting/integration_scripting_happy_path.cpp
    scripting
    LINK_LIBS sle::scripting
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_scripting_edge_path
    scripting/integration_scripting_edge_path.cpp
    scripting
    LINK_LIBS sle::scripting
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_scripting_state_machine_api_surface_path
    scripting/integration_scripting_state_machine_api_surface_path.cpp
    scripting
    LINK_LIBS sle::scripting
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

# UI
sle_add_integration_test(
    integration_ui_happy_path
    ui/integration_ui_happy_path.cpp
    ui
    LINK_LIBS sle::ui
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_ui_edge_path
    ui/integration_ui_edge_path.cpp
    ui
    LINK_LIBS sle::ui
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

# Renderer
sle_add_integration_test(
    integration_renderer_happy_path
    renderer/integration_renderer_happy_path.cpp
    "renderer;windowed"
    LINK_LIBS sle::renderer sle::platform
)

sle_add_integration_test(
    integration_renderer_edge_path
    renderer/integration_renderer_edge_path.cpp
    "renderer;windowed"
    LINK_LIBS sle::renderer sle::platform
)

# Systems
sle_add_integration_test(
    integration_state_machine_happy_path
    systems/integration_state_machine_happy_path.cpp
    systems
    LINK_LIBS sle::engine
)

sle_add_integration_test(
    integration_state_machine_timed_transition_path
    systems/integration_state_machine_timed_transition_path.cpp
    systems
    LINK_LIBS sle::engine
)

sle_add_integration_test(
    integration_state_machine_transition_event_path
    systems/integration_state_machine_transition_event_path.cpp
    systems
    LINK_LIBS sle::engine
)

sle_add_integration_test(
    integration_state_machine_script_callbacks_path
    systems/integration_state_machine_script_callbacks_path.cpp
    systems
    LINK_LIBS sle::engine
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_state_machine_script_callbacks_missing_edge_path
    systems/integration_state_machine_script_callbacks_missing_edge_path.cpp
    systems
    LINK_LIBS sle::engine
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_state_machine_asset_definition_happy_path
    systems/integration_state_machine_asset_definition_happy_path.cpp
    systems
    LINK_LIBS sle::engine
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_state_machine_lua_guard_transition_path
    systems/integration_state_machine_lua_guard_transition_path.cpp
    systems
    LINK_LIBS sle::engine
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_animation_interpolation_binding_path
    systems/integration_animation_interpolation_binding_path.cpp
    systems
    LINK_LIBS sle::engine
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_animation_vec2_vec4_other_entity_path
    systems/integration_animation_vec2_vec4_other_entity_path.cpp
    systems
    LINK_LIBS sle::engine
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_animation_state_machine_clip_map_path
    systems/integration_animation_state_machine_clip_map_path.cpp
    systems
    LINK_LIBS sle::engine
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

sle_add_integration_test(
    integration_scripting_animator_api_surface_path
    scripting/integration_scripting_animator_api_surface_path.cpp
    scripting
    LINK_LIBS sle::scripting
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
)

