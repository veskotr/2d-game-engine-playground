cmake_minimum_required(VERSION 3.20)
project(GameEngine LANGUAGES CXX)

include(CTest)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(MSVC)
	# Keep all targets on the same CRT to avoid LNK4098 (LIBCMT vs MSVCRT).
	set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")

	# Some third-party projects hardcode /MT(/MTd). Normalize all flag sets.
	foreach(flag_var
		CMAKE_C_FLAGS CMAKE_C_FLAGS_DEBUG CMAKE_C_FLAGS_RELEASE CMAKE_C_FLAGS_RELWITHDEBINFO CMAKE_C_FLAGS_MINSIZEREL
		CMAKE_CXX_FLAGS CMAKE_CXX_FLAGS_DEBUG CMAKE_CXX_FLAGS_RELEASE CMAKE_CXX_FLAGS_RELWITHDEBINFO CMAKE_CXX_FLAGS_MINSIZEREL)
		if(DEFINED ${flag_var})
			string(REPLACE "/MTd" "/MDd" ${flag_var} "${${flag_var}}")
			string(REPLACE "/MT" "/MD" ${flag_var} "${${flag_var}}")
		endif()
	endforeach()
endif()

# Export compile commands (useful for VSCode IntelliSense)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# -------------------------
# Dependencies
# -------------------------
include(cmake/Dependencies.cmake)

# -------------------------
# Layered engine modules
# -------------------------
add_subdirectory(EngineModules)

# -------------------------
# Standalone examples/samples
# -------------------------
add_subdirectory(engine_app)
add_subdirectory(examples)

# -------------------------
# Tests
# -------------------------
if(BUILD_TESTING)
	add_subdirectory(tests)
endif()
