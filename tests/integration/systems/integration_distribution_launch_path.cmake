if(NOT EXISTS "${ENGINE_APP}")
    message(FATAL_ERROR "Build engine_app before running distribution launch checks")
endif()
string(RANDOM LENGTH 12 ALPHABET 0123456789abcdef token)
set(root "${TEST_ROOT}/distribution-launch-${token}")
set(delivery "${root}/relocated delivery")
file(MAKE_DIRECTORY "${delivery}/game/Assets" "${root}/elsewhere")
get_filename_component(executable "${ENGINE_APP}" NAME)
file(COPY "${ENGINE_APP}" DESTINATION "${delivery}")
file(WRITE "${delivery}/game/game.sleproject" [=[{
  "format":"sle-project","version":1,"id":"11111111-1111-1111-1111-111111111111",
  "name":"Distribution","assetRoot":"Assets","startupScene":null,
  "game":{"window":{"title":"Distribution","width":640,"height":360,"vsync":false,"mode":"Windowed"},"targetFPS":0}
}]=])
# A decoy config in cwd must not replace the delivered game.
file(WRITE "${root}/elsewhere/engine.json" "invalid decoy config")
function(check_project_discovery invocation)
    execute_process(COMMAND ${ARGN} --frames 1
        WORKING_DIRECTORY "${root}/elsewhere" RESULT_VARIABLE result
        OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 15)
    if(NOT "${result}" STREQUAL "1" OR NOT "${output}${errors}" MATCHES "Choose a startup scene")
        message(FATAL_ERROR "${invocation} launch did not discover its project (exit ${result}): ${output}${errors}")
    endif()
endfunction()
check_project_discovery(absolute "${delivery}/${executable}")
if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Linux")
    check_project_discovery(relative "../relocated delivery/${executable}")
    check_project_discovery(PATH "${CMAKE_COMMAND}" -E env "PATH=${delivery}:$ENV{PATH}" "${executable}")
    file(CREATE_LINK "../relocated delivery/${executable}" "${root}/elsewhere/game-link" SYMBOLIC)
    check_project_discovery(symlink "./game-link")
endif()
# Explicit config must retain precedence over the delivered default.
execute_process(COMMAND "${delivery}/${executable}" --config "${root}/elsewhere/engine.json" --frames 1
    WORKING_DIRECTORY "${root}/elsewhere" RESULT_VARIABLE result
    OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 15)
if(result EQUAL 0 OR NOT "${output}${errors}" MATCHES "Malformed engine JSON")
    message(FATAL_ERROR "Explicit config was not respected: ${result}: ${output}${errors}")
endif()
execute_process(COMMAND "${delivery}/${executable}" --project missing.sleproject --frames 1
    WORKING_DIRECTORY "${root}/elsewhere" RESULT_VARIABLE result
    OUTPUT_VARIABLE output ERROR_VARIABLE errors TIMEOUT 15)
if(NOT "${result}" STREQUAL "1" OR NOT "${output}${errors}" MATCHES "Project launch:"
        OR NOT "${output}${errors}" MATCHES "missing\\.sleproject")
    message(FATAL_ERROR "Explicit project was not respected: ${result}: ${output}${errors}")
endif()
file(REMOVE_RECURSE "${root}")
