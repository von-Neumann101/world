#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform mat4 InverseViewProjection;
uniform mat4 ViewProjection;
uniform vec3 CameraFromOrigin;
uniform float ScanProgress;
uniform float DistortionStrength;
in vec2 texCoord;
out vec4 fragColor;

vec3 relativePosition(vec2 uv, float depth) {
    vec4 p = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

void main() {
    // One continuous mapping for the entire world image. Scene depth must never gate
    // displacement: doing so tears the sampling field at every block/entity silhouette.
    // The preceding color pass still renders the advancing entry scan through the world.
    float coverage = smoothstep(0.08, 1.0, ScanProgress);

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
    vec2 aspect = vec2(InSize.x / InSize.y, 1.0);
    // Bound in both UV and viewport-height units. A UV-only horizontal cap grows
    // with ultrawide windows and can fold the field around the fixed-size reticle.
    float extent = max(length(delta) / 0.075, length(delta * aspect) / 0.12);
    delta /= max(1.0, extent);

    // Broad, smooth protection at the frame and reticle, independent of object boundaries.
    // Keep the crosshair ray exact while allowing off-axis foreground to follow the space.
    vec2 border = min(texCoord, 1.0 - texCoord);
    vec2 frameAxes = smoothstep(vec2(0.008), vec2(0.20), border);
    float frame = frameAxes.x * frameAxes.y;
    vec2 fromReticle = (texCoord - 0.5) * aspect;
    float aiming = smoothstep(0.08, 0.32, length(fromReticle));
    delta *= coverage * clamp(DistortionStrength, 0.0, 1.0) * frame * aiming;
    vec2 halfPixel = 0.5 / InSize;
    vec2 source = clamp(texCoord + delta, halfPixel, 1.0 - halfPixel);
    // Run after the unfiltered skill/residual layer; never desaturate or dim that composition.
    fragColor = vec4(texture(DiffuseSampler, source).rgb, 1.0);
}
