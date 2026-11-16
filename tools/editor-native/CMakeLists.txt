if(NOT (WIN32 OR CMAKE_SYSTEM_NAME STREQUAL "Linux") OR NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "The embedded editor host supports Windows x64 and Linux x64 only")
endif()

find_package(JNI REQUIRED)

add_library(sle_editor_native SHARED EditorBridge.cpp)
target_include_directories(sle_editor_native PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(sle_editor_native PRIVATE sle::engine)
