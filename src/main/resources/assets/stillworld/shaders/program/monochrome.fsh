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

    // Keep the original lit texture, with a lifted charcoal floor instead of thresholding it to black.
    float surface = 0.075 + 0.39 * pow(max(luminance, 0.0), 0.88);
    float haze = smoothstep(24.0, ScanRange, distanceFromEye) * 0.32;
    surface = mix(surface, 0.29, haze);
    if (sky) surface = 0.19 + luminance * 0.13;

    float edge = texture(EdgeSampler, texCoord).r;
    // Thin isolated structure stays bright. Repeated cutout holes (leaves/grass) remain texture,
    // instead of turning an entire canopy into a mass of white stippling.
    float density = edge;
    density += texture(EdgeSampler, texCoord + pixel * vec2(2.0, 0.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(-2.0, 0.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(0.0, 2.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(0.0, -2.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(3.0, 3.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(-3.0, 3.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(3.0, -3.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(-3.0, -3.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(5.0, 0.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(-5.0, 0.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(0.0, 5.0)).r;
    density += texture(EdgeSampler, texCoord + pixel * vec2(0.0, -5.0)).r;
    edge *= 1.0 - 0.92 * smoothstep(0.23, 0.55, density / 13.0);
    edge *= 1.0 - 0.6 * smoothstep(32.0, 140.0, distanceFromEye);
    if (sky) edge = 0.0;
    float linework = mix(surface, 0.86, edge * 0.94);
    vec3 result = mix(color.rgb, vec3(linework), coverage);
    // Narrow silver-white core and restrained halo follow world distance, not a screen-space circle.
    result = mix(result, vec3(0.9, 0.94, 0.98), front * 0.65);
    fragColor = vec4(result, color.a);
}
