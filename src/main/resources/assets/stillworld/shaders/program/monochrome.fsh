#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D EdgeSampler;
uniform vec2 InSize;
uniform mat4 InverseViewProjection;
uniform vec3 CameraFromOrigin;
uniform float ScanProgress;
uniform float ScanRadius;
uniform float ScanRange;
uniform float Elapsed;
uniform float DistortionStrength;
in vec2 texCoord;
out vec4 fragColor;

vec3 relativePosition(vec2 uv, float depth) {
    vec4 p = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

void main() {
    vec2 pixel = 1.0 / InSize;
    float depth = texture(DepthSampler, texCoord).r;
    bool sky = depth >= 0.999999;
    vec3 position = relativePosition(texCoord, depth);
    float distanceFromEye = length(position);
    float distanceFromEntry = sky ? ScanRange * 0.9 : length(position + CameraFromOrigin);
    float width = 2.0 + ScanRadius * 0.014;
    float signedFront = (distanceFromEntry - ScanRadius) / width;
    float fadeFront = 1.0 - smoothstep(0.86, 1.0, ScanProgress);
    float front = exp(-signedFront * signedFront * 3.0) * fadeFront;
    float coverage = 1.0 - smoothstep(-1.0, 1.0, signedFront);
    coverage = mix(coverage, 1.0, smoothstep(0.86, 1.0, ScanProgress));

    // A sub-two-pixel refraction confined to the wave; no persistent wobble or chromatic fringing.
    vec2 direction = normalize((texCoord - 0.5) * InSize + vec2(0.01));
    vec2 warp = direction * pixel * (1.35 * front * sin(signedFront * 4.0 - Elapsed * 9.0)) * DistortionStrength;
    vec4 color = texture(DiffuseSampler, clamp(texCoord + warp, pixel * 0.5, 1.0 - pixel * 0.5));
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Silver-gray surfaces retain the original texture and lighting; contact shadows stay grounded.
    float surface = 0.14 + 0.52 * pow(max(luminance, 0.0), 0.85);
    // Distance removes contrast into a luminous atmosphere instead of multiplying everything to black.
    // Logarithmic world distance with a soft ceiling keeps some separation even at long view distances.
    float opticalDistance = log(1.0 + max(distanceFromEye - 8.0, 0.0) / 24.0);
    float haze = 0.83 * (1.0 - exp(-0.60 * opticalDistance));
    float atmosphere = 0.66;
    if (sky) {
        surface = atmosphere + 0.10 * luminance;
        haze = 0.0;
    }

    float edge = texture(EdgeSampler, texCoord).r;
    // Thin isolated structure gets a quiet rim. Repeated cutout holes (leaves/grass) remain texture,
    // instead of turning an entire canopy into a mass of white stippling.
    float density = texture(EdgeSampler, texCoord).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(2.0, 0.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(-2.0, 0.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(0.0, 2.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(0.0, -2.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(3.0, 3.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(-3.0, 3.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(3.0, -3.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(-3.0, -3.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(5.0, 0.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(-5.0, 0.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(0.0, 5.0)).g;
    density += texture(EdgeSampler, texCoord + pixel * vec2(0.0, -5.0)).g;
    edge *= 1.0 - 0.99 * smoothstep(0.23, 0.50, density / 13.0);
    edge *= 1.0 - 0.8 * smoothstep(12.0, 80.0, distanceFromEye);
    if (sky) edge = 0.0;
    // Contours briefly catch the entry wave, then settle back into the surface rather than dominating it.
    float rim = edge * (0.30 + front * 0.38);
    float linework = mix(mix(surface, 0.86, rim), atmosphere, haze);
    vec3 result = mix(color.rgb, vec3(linework), coverage);
    // Narrow silver-white core and restrained halo follow world distance, not a screen-space circle.
    result = mix(result, vec3(0.9, 0.94, 0.98), front * 0.65);
    // This is the completed world image. Empty sky may have alpha zero in the source framebuffer;
    // carrying that alpha into the final blended blit would erase the atmosphere into a black horizon.
    fragColor = vec4(result, 1.0);
}
