if(NOT EXISTS "${ENGINE_APP}")
    message(FATAL_ERROR "Build engine_app before running distribution launch checks")
endif()
string(RANDOM LENGTH 12 ALPHABET 0123456789abcdef token)
set(root "${TEST_ROOT}/distribution-launch-${token}")
file(MAKE_DIRECTORY "${root}/delivery/game/Assets" "${root}/elsewhere")
get_filename_component(executable "${ENGINE_APP}" NAME)
file(COPY "${ENGINE_APP}" DESTINATION "${root}/delivery")
file(WRITE "${root}/delivery/game/game.sleproject" [=[{
  "format":"sle-project","version":1,"id":"11111111-1111-1111-1111-111111111111",
  "name":"Distribution","assetRoot":"Assets","startupScene":null,
  "game":{"window":{"title":"Distribution","width":640,"height":360,"vsync":false,"mode":"Windowed"},"targetFPS":0}
}]=])
# A decoy config in cwd must not replace the delivered game.
file(WRITE "${root}/elsewhere/engine.json" "invalid decoy config")
execute_process(COMMAND "${root}/delivery/${executable}" --frames 1
    WORKING_DIRECTORY "${root}/elsewhere" RESULT_VARIABLE result
    OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 15)
if(result EQUAL 0 OR NOT "${output}${errors}" MATCHES "Choose a startup scene")
    message(FATAL_ERROR "No-config delivery launch did not discover its project (exit ${result}): ${output}${errors}")
endif()
# Explicit config must retain precedence over the delivered default.
execute_process(COMMAND "${root}/delivery/${executable}" --config "${root}/elsewhere/engine.json" --frames 1
    WORKING_DIRECTORY "${root}/elsewhere" RESULT_VARIABLE result
    OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 15)
if(result EQUAL 0 OR NOT "${output}${errors}" MATCHES "Malformed engine JSON")
    message(FATAL_ERROR "Explicit config was not respected: ${result}: ${output}${errors}")
endif()
file(REMOVE_RECURSE "${root}")
