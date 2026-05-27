#version 330 core

layout(location=0) in vec2 aPos;
layout(location=1) in vec2 aUV;

layout(location=2) in mat4 iModel;
layout(location=6) in vec4 iColor;
layout(location=7) in vec4 iUVRect;

uniform mat4 uVP;

out vec2 vUV;
out vec4 vColor;

void main()
{
    // remap 0..1 quad UV → atlas rect
    vUV = mix(iUVRect.xy, iUVRect.zw, aUV);
    vColor = iColor;

    gl_Position = uVP * iModel * vec4(aPos, 0.0, 1.0);
}