#version 150
uniform sampler2D Sampler1;
uniform vec2 ViewportSize;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

float corner(float x, float center, float radius) {
    return max(0.0, 1.0 - abs(x - center) / radius);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float taper = max(0.0, 1.0 - abs(p.x));
    // Few straight segments, with unequal notches: a cut rather than a wavy portal.
    float spine = 0.66 * p.x + 0.035 * corner(p.x, -0.18, 0.38)
            - 0.018 * corner(p.x, 0.45, 0.30);
    float upper = taper * (0.22 - 0.065 * corner(p.x, 0.28, 0.08));
    float lower = -taper * (0.18 - 0.045 * corner(p.x, -0.36, 0.07));
    float across = p.y - spine;
    float edge = min(upper - across, across - lower);
    float aa = max(fwidth(edge) * 0.65, 0.00001);
    float coverage = smoothstep(-aa, aa, edge);
    if(coverage < 0.01) discard;

    vec2 halfPixel = 0.5 / vec2(textureSize(Sampler1, 0));
    vec2 screen = clamp(gl_FragCoord.xy / ViewportSize, halfPixel, 1.0 - halfPixel);
    vec3 live = texture(Sampler1, screen).rgb;
    // Both rims taper with the cut, so neither end becomes a blunt luminous cap.
    float opening = smoothstep(0.028 * taper - aa, 0.028 * taper + aa, edge);
    float silver = 1.0 - smoothstep(0.006 * taper - aa, 0.006 * taper + aa, edge);
    vec3 color = mix(vec3(0.015, 0.019, 0.024), live, opening);
    color = mix(color, vec3(0.84, 0.88, 0.94), silver * 0.9);
    fragColor = vec4(color, coverage) * vertexColor * ColorModulator;
}
