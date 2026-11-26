# Built-game delivery: engine_app beside config.json and .slepkg packages.
# MODE=discovery needs no window; MODE=launch runs gameplay frames (windowed).
if(NOT EXISTS "${ENGINE_APP}" OR NOT EXISTS "${FIXTURE}")
    message(FATAL_ERROR "Build engine_app and integration_game_package_path first")
endif()
string(RANDOM LENGTH 12 ALPHABET 0123456789abcdef token)
set(root "${TEST_ROOT}/package-launch-${token}")
file(MAKE_DIRECTORY "${root}/delivery" "${root}/elsewhere")
get_filename_component(executable "${ENGINE_APP}" NAME)
file(COPY "${ENGINE_APP}" DESTINATION "${root}/delivery")
# A decoy engine.json in cwd must not replace the delivered config.
file(WRITE "${root}/elsewhere/engine.json" "invalid decoy config")

if(MODE STREQUAL "discovery")
    # The executable reports its engine version without loading any game.
    execute_process(COMMAND "${root}/delivery/${executable}" --version
        RESULT_VARIABLE result OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 15)
    if(NOT result EQUAL 0 OR NOT "${output}" MATCHES "^SLE engine [0-9]+\\.[0-9]+\\.[0-9]+")
        message(FATAL_ERROR "--version failed (exit ${result}): ${output}${errors}")
    endif()
    # Packages without a main scene: the error proves config.json beside the exe was used.
    execute_process(COMMAND "${FIXTURE}" --fixture "${root}/delivery" --no-main RESULT_VARIABLE result)
    if(NOT result EQUAL 0)
        message(FATAL_ERROR "Fixture generation failed")
    endif()
    execute_process(COMMAND "${root}/delivery/${executable}" --frames 1
        WORKING_DIRECTORY "${root}/elsewhere" RESULT_VARIABLE result
        OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 15)
    if(result EQUAL 0 OR NOT "${output}${errors}" MATCHES "No game package declares a main scene")
        message(FATAL_ERROR "No-option launch did not use the delivered config.json (exit ${result}): ${output}${errors}")
    endif()
    # Explicit --config keeps precedence over the delivered default.
    execute_process(COMMAND "${root}/delivery/${executable}" --config "${root}/elsewhere/engine.json" --frames 1
        WORKING_DIRECTORY "${root}/elsewhere" RESULT_VARIABLE result
        OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 15)
    if(result EQUAL 0 OR NOT "${output}${errors}" MATCHES "Malformed engine JSON")
        message(FATAL_ERROR "Explicit config was not respected: ${result}: ${output}${errors}")
    endif()
else()
    if(NOT EXISTS "${SAMPLER_TEST}")
        message(FATAL_ERROR "Build integration_project_play_path first")
    endif()
    foreach(folder IN ITEMS shaders ui fonts)
        file(COPY "${SOURCE_ROOT}/assets/${folder}" DESTINATION "${root}/delivery/assets")
    endforeach()
    foreach(filter IN ITEMS nearest linear default pixelArt smooth)
        execute_process(COMMAND "${FIXTURE}" --fixture "${root}/delivery" "${filter}" RESULT_VARIABLE result)
        if(NOT result EQUAL 0)
            message(FATAL_ERROR "Fixture generation failed for ${filter}")
        endif()
        # The first package switches to the second, which has the filtered sprite.
        execute_process(COMMAND "${root}/delivery/${executable}" --frames 8
            WORKING_DIRECTORY "${root}/elsewhere" RESULT_VARIABLE result
            OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 30)
        if(NOT result EQUAL 0 OR NOT "${output}${errors}" MATCHES "Completed 8 frames; scene: second")
            message(FATAL_ERROR "Packaged game launch failed for ${filter} (exit ${result}): ${output}${errors}")
        endif()
        if("${output}${errors}" MATCHES "cross-package module missing|dynamic project asset lookup failed|packaged tag query failed|failed to load texture|sprite region .*missing or outside")
            message(FATAL_ERROR "A packaged dependency failed to load for ${filter}: ${output}${errors}")
        endif()
        execute_process(COMMAND "${SAMPLER_TEST}" --check-package-sampler "${root}/delivery/config.json" "${filter}"
            WORKING_DIRECTORY "${SOURCE_ROOT}" RESULT_VARIABLE result
            OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 30)
        if(NOT result EQUAL 0)
            message(FATAL_ERROR "Packaged sprite sampler or region failed for ${filter} (exit ${result}): ${output}${errors}")
        endif()
    endforeach()
endif()
file(REMOVE_RECURSE "${root}")
