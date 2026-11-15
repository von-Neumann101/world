#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D RealitySampler;
uniform mat4 InverseViewProjection;
uniform mat4 ViewProjection;
uniform vec3 SphereCenter;
uniform float SphereRadius;
uniform float Opacity;
uniform float AnimationTime;
uniform vec2 InSize;

in vec2 texCoord;
out vec4 fragColor;

vec3 relativePosition(vec2 uv, float depth) {
    vec4 point = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return point.xyz / point.w;
}

float boundaryWave(vec3 direction) {
    // World-oriented, smooth waves with different speeds: local lobes flow around
    // the opening rather than the whole silhouette breathing as one perfect circle.
    return 0.070 * sin(dot(direction, vec3(3.7, 1.3, 2.1)) + AnimationTime * 0.85)
         + 0.040 * sin(dot(direction, vec3(-1.7, 4.9, 2.8)) - AnimationTime * 1.10)
         + 0.018 * sin(dot(direction, vec3(6.2, -3.4, 4.6)) + AnimationTime * 1.45);
}

void main() {
    vec3 frozen = texture(DiffuseSampler, texCoord).rgb;
    fragColor = vec4(frozen, 1.0);
    if (Opacity <= 0.0 || SphereRadius <= 0.0) return;

    // The view matrix is rotation-only. Recover the perspective eye as well as a
    // ray line so walking-bob translation in the projection remains consistent
    // with the main scene's depth, rather than turning into an apparent orb drift.
    vec4 eye = InverseViewProjection * vec4(0.0, 0.0, 1.0, 0.0);
    if (abs(eye.w) < 0.000001) return;
    vec3 origin = eye.xyz / eye.w;
    vec3 ray = normalize(relativePosition(texCoord, 0.9) - relativePosition(texCoord, 0.0));
    vec3 offset = origin - SphereCenter;
    // Walking inside the effect never replaces the entire viewport. The runtime
    // also hides it there; this keeps near-surface and inside-eye samples finite.
    float envelope = SphereRadius * 1.15;
    if (dot(offset, offset) <= envelope * envelope) return;
    float b = dot(offset, ray);
    vec3 closest = offset - b * ray;
    float closestLength = length(closest);
    float baseRadial = closestLength / SphereRadius;
    if (baseRadial > 1.15) return;
    vec3 direction = closest / max(closestLength, 0.00001);
    // A directional effective radius keeps the existing analytic depth test cheap.
    // Only the perimeter deforms; the central live image and front depth stay steady.
    float radius = SphereRadius * (1.0 + boundaryWave(direction) * smoothstep(0.35, 0.75, baseRadial));
    float c = dot(offset, offset) - radius * radius;
    float discriminant = b * b - c;
    float radial = closestLength / radius;
    float pixelWidth = max(fwidth(radial), 0.00001);
    if (discriminant <= 0.0) return;
    float hitDistance = -b - sqrt(discriminant);
    if (hitDistance <= 0.0001) return;

    // The deformed shell remains occluded by the existing frozen scene. Sampling
    // main depth at the destination pixel keeps nearby blocks in front of it.
    float sceneDepth = texture(DepthSampler, texCoord).r;
    if (sceneDepth < 1.0) {
        float sceneDistance = dot(relativePosition(texCoord, sceneDepth) - origin, ray);
        if (sceneDistance < hitDistance - 0.002) return;
    }

    // The center is the untouched live color image. Only grazing rays acquire a
    // mild lens curvature and a dark surface falloff, without tracing block edges.
    vec2 liveUv = texCoord;
    vec4 projectedCenter = ViewProjection * vec4(SphereCenter, 1.0);
    if (projectedCenter.w > 0.0001) {
        vec2 centerUv = projectedCenter.xy / projectedCenter.w * 0.5 + 0.5;
        float edgeCurve = smoothstep(0.55, 0.93, radial);
        edgeCurve *= edgeCurve * (1.0 - smoothstep(0.94, 1.0, radial));
        liveUv += (texCoord - centerUv) * (0.055 * edgeCurve);
    }
    vec2 halfPixel = 0.5 / max(InSize, vec2(1.0));
    liveUv = clamp(liveUv, halfPixel, 1.0 - halfPixel);
    vec3 live = texture(RealitySampler, liveUv).rgb;
    float grazing = smoothstep(0.68, 0.98, radial);
    vec3 surface = live * (1.0 - 0.92 * pow(grazing, 1.35));

    // A restrained silver rim just inside the silhouette, roughly one pixel wide
    // at a distance. The dark falloff and silver accent follow the same moving edge.
    float ringWidth = max(0.004, pixelWidth * 0.35);
    float ringCoordinate = (radial - 0.978) / ringWidth;
    float ring = exp(-ringCoordinate * ringCoordinate) * 0.58;
    surface = mix(surface, vec3(0.70, 0.74, 0.78), ring);
    float coverage = 1.0 - smoothstep(1.0 - pixelWidth * 0.7, 1.0, radial);
    fragColor = vec4(mix(frozen, surface, coverage * clamp(Opacity, 0.0, 1.0)), 1.0);
}
