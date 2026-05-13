 #version 460 core
in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform vec4 uColor;

out vec4 FragColor;

void main() {
    vec4 texColor = texture(uTexture, vTexCoord);
    FragColor = texColor * uColor;
}