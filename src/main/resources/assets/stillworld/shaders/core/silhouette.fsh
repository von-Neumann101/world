#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    float mask = texture(Sampler0, texCoord0).a;
    if (mask < 0.1) discard;
    vec4 color = vertexColor * ColorModulator;
    color.a *= mask;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
