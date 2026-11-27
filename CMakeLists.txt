cmake_minimum_required(VERSION 3.20)
# GLAD and audio decoder targets compile C in this directory scope.
# Enable C here so MSVC runtime selection also applies to those objects.
# Engine version: 0.MINOR.PATCH while pre-1.0; bump MINOR for each added engine feature.
project(GameEngine VERSION 0.35.0 LANGUAGES C CXX)

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

# Editor embedding is opt-in. Standalone builds do not require a JDK.
option(SLE_BUILD_EDITOR "Build the editor JNI library (Windows x64, Linux x64)" OFF)
if(SLE_BUILD_EDITOR)
	# The JNI shared library links the engine's static libraries and dependencies.
	set(CMAKE_POSITION_INDEPENDENT_CODE ON)
	if(MSVC)
		# The JVM loads its own msvcp140.dll, which may predate this toolset. Since VS 17.10
		# the inline constexpr std::mutex constructor crashes with runtimes older than 14.40.
		add_compile_definitions(_DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR)
	endif()
endif()

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

if(SLE_BUILD_EDITOR)
    add_subdirectory(tools/editor-native)
endif()

# -------------------------
# Tests
# -------------------------
if(BUILD_TESTING)
	add_subdirectory(tests)
endif()
