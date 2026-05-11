 #version 460 core
layout(location = 0) in vec2 aPos;

uniform mat4 uVP;
uniform vec2 uPos;
uniform vec2 uSize;

void main() {
    vec2 world = aPos * uSize + uPos;
    gl_Position = uVP * vec4(world, 0.0, 1.0);
}