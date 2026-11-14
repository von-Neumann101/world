#version 150
uniform sampler2D DepthSampler;
uniform vec2 OutSize;
uniform mat4 InverseViewProjection;
uniform vec3 CameraFromOrigin;
uniform float ScanRange;
in vec2 texCoord;
out vec4 fragColor;

vec3 relativePosition(vec2 uv, float depth) {
    vec4 p = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float radialStrength(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    vec3 position;
    if (depth < 0.999999) {
        position = relativePosition(uv, depth);
    } else {
        // Sky has no surface distance; use the visible-world range as its approximation.
        vec3 ray = normalize(relativePosition(uv, 0.9) - relativePosition(uv, 0.0));
        vec4 eye = InverseViewProjection * vec4(0.0, 0.0, 1.0, 0.0);
        position = eye.xyz / eye.w + ray * ScanRange;
    }
    float distance = length(position + CameraFromOrigin);
    // The slopes nearly match at 64 blocks, without an extra transition branch.
    return log(1.0 + min(distance, 64.0) / 16.0) / log(5.0)
        + max(distance - 64.0, 0.0) / 128.0;
}

void main() {
    // Four scene samples per coarse cell keep the work independent of window size.
    vec2 step = 0.25 / OutSize;
    float strength = radialStrength(texCoord + vec2(-step.x, -step.y))
        + radialStrength(texCoord + vec2(step.x, -step.y))
        + radialStrength(texCoord + vec2(-step.x, step.y))
        + radialStrength(texCoord + vec2(step.x, step.y));
    // PostChain targets are RGBA8: encode a wider range than [0, 1].
    fragColor = vec4(vec3(strength / (4.0 * 16.0)), 1.0);
}
