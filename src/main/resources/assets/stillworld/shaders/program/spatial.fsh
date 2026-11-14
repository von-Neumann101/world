#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 InSize;
uniform mat4 InverseViewProjection;
uniform mat4 ViewProjection;
uniform vec3 CameraFromOrigin;
uniform float ScanProgress;
uniform float ScanRadius;
uniform float ScanRange;
uniform float DistortionStrength;
in vec2 texCoord;
out vec4 fragColor;

vec3 relativePosition(vec2 uv, float depth) {
    vec4 p = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float distantWeight(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    if (depth >= 0.999999) return 1.0;
    // Leave interaction and nearby silhouettes in their actual screen positions.
    return smoothstep(12.0, 48.0, length(relativePosition(uv, depth)));
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    vec3 position = relativePosition(texCoord, depth);
    float distanceFromEntry = depth >= 0.999999 ? ScanRange * 0.9 : length(position + CameraFromOrigin);
    float width = 2.0 + ScanRadius * 0.014;
    float coverage = 1.0 - smoothstep(-1.0, 1.0, (distanceFromEntry - ScanRadius) / width);
    coverage = mix(coverage, 1.0, smoothstep(0.86, 1.0, ScanProgress));
    coverage *= smoothstep(0.0, 0.08, ScanProgress);

    // Refract through a broad, stationary field in world axes, not a lens fixed to the screen.
    // A shared optical shell keeps sky/terrain and distant object boundaries continuous.
    // There is deliberately no elapsed-time term: the changed space settles with the entry wave.
    // Minecraft includes walking-bob translation in its projection. Unproject a line,
    // not a point near the camera: normalizing that point would amplify the bob.
    vec3 ray = normalize(relativePosition(texCoord, 0.9) - relativePosition(texCoord, 0.0));
    vec4 eye = InverseViewProjection * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 opticalOrigin = eye.xyz / eye.w;
    vec3 shell = opticalOrigin + ray * 96.0;
    vec3 world = shell + CameraFromOrigin;
    vec3 bend = 2.2 * vec3(
        5.0 * sin(world.z * 0.014) + 3.0 * sin(world.y * 0.018),
        4.5 * sin(world.x * 0.012 + 0.7) + 3.0 * sin(world.z * 0.009),
        4.0 * sin(world.x * 0.013) - 2.0 * sin(world.y * 0.016)
    );
    vec4 projected = ViewProjection * vec4(shell - bend, 1.0);
    vec2 refracted = projected.xy / projected.w * 0.5 + 0.5;
    vec2 delta = refracted - texCoord;
    delta *= min(1.0, 0.075 / max(length(delta), 0.00001));

    // Fade at the border instead of stretching clamped edge pixels into missing geometry.
    vec2 border = min(texCoord, 1.0 - texCoord);
    float frame = smoothstep(0.008, 0.10, min(border.x, border.y));
    float amount = coverage * clamp(DistortionStrength, 0.0, 1.0) * frame;
    delta *= amount;
    float protection = distantWeight(texCoord);
    vec2 halfPixel = 0.5 / InSize;
    vec2 candidate = clamp(texCoord + delta * protection, halfPixel, 1.0 - halfPixel);
    // Protect foreground sampled along the refraction path, not just at its destination.
    // Combine depth weights with min: multiplying them suppresses the midrange twice.
    protection = min(protection, distantWeight(candidate));
    protection = min(protection, distantWeight(mix(texCoord, candidate, 0.5)));
    protection = min(protection, distantWeight(mix(texCoord, candidate, 0.25)));
    protection = min(protection, distantWeight(mix(texCoord, candidate, 0.75)));
    vec2 source = clamp(texCoord + delta * protection, halfPixel, 1.0 - halfPixel);
    // Run after the unfiltered skill/residual layer; never desaturate or dim that composition.
    fragColor = vec4(texture(DiffuseSampler, source).rgb, 1.0);
}
