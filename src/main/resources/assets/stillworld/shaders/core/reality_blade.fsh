#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 ViewportSize;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    float aperture = texture(Sampler0, texCoord0).a;
    if (aperture < 0.1) discard;
    vec2 uv = gl_FragCoord.xy / ViewportSize;
    vec2 halfPixel = 0.5 / vec2(textureSize(Sampler1, 0));
    vec3 live = texture(Sampler1, clamp(uv, halfPixel, 1.0 - halfPixel)).rgb;
    fragColor = vec4(live, aperture) * vertexColor * ColorModulator;
}
