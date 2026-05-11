#pragma once

#ifdef ENGINE_DEBUG
#include <glad/gl.h>
#include "Log.h"

inline void GLCheck(const char* file, int line)
{
    while (GLenum err = glGetError())
    {
        Log::error("[GL ERROR] {} at {}:{}", err, file, line);
    }
}

#define GL_CALL(x) x; GLCheck(__FILE__, __LINE__)

#else
#define GL_CALL(x) x
#endif