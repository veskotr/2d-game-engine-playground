#pragma once
#define ENGINE_DEBUG
#ifdef ENGINE_DEBUG
#include <glad/gl.h>
#include <sle/core/Log.hpp>

namespace sle::core {

inline void GLCheck(const char* file, int line)
{
    GLenum err = glGetError();
    if (err != GL_NO_ERROR)
    {
        Log::error("[GL ERROR] {} at {}:{}", err, file, line);
        // Don't loop - this was causing spam. OpenGL will queue errors, 
        // but we should handle them as they occur, not re-check repeatedly.
    }
}

} // namespace sle::core

#define GL_CALL(x) x; ::sle::core::GLCheck(__FILE__, __LINE__)

#else
#define GL_CALL(x) x
#endif
