#version 150
in vec3 Position;
uniform vec2 OutSize;
out vec2 texCoord;

void main() {
    // PostChain supplies a window-sized ProjMat even for fixed-size targets.
    // Normalize the actual output quad so every coarse-field texel is covered.
    texCoord = Position.xy / OutSize;
    gl_Position = vec4(texCoord * 2.0 - 1.0, 0.2, 1.0);
}
