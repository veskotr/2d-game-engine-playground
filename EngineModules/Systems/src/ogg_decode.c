/*
 * ogg_decode.c
 * Pure C translation unit. Compiles stb_vorbis and exposes a minimal
 * decode-to-PCM API so C++ code can consume OGG without header conflicts.
 */
#include "ogg_decode.h"

#define STB_VORBIS_NO_STDIO 0
#include <stb_vorbis.c>

#include <stdlib.h>

short* ogg_decode_file(const char* path, int* outChannels, int* outSampleRate, int* outFrameCount)
{
    int channels = 0;
    int sampleRate = 0;
    short* pcm = NULL;
    int samples = stb_vorbis_decode_filename(path, &channels, &sampleRate, &pcm);
    if (samples < 0 || !pcm)
        return NULL;

    *outChannels   = channels;
    *outSampleRate = sampleRate;
    *outFrameCount = channels > 0 ? samples / channels : 0;
    return pcm;
}

void ogg_free(short* data)
{
    free(data);
}
