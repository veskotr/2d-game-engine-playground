#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Decodes an OGG/Vorbis file to interleaved 16-bit PCM using stb_vorbis.
 * Returns a heap-allocated buffer that must be freed with ogg_free().
 * Returns NULL on failure.
 */
short* ogg_decode_file(const char* path, int* outChannels, int* outSampleRate, int* outFrameCount);
void   ogg_free(short* data);

#ifdef __cplusplus
}
#endif
