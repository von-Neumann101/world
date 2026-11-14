#version 150
uniform sampler2D DepthSampler;
uniform vec2 InSize;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 pixel = 1.0 / InSize;
    float depth = texture(DepthSampler, texCoord).r;
    float left = texture(DepthSampler, texCoord - vec2(pixel.x, 0.0)).r;
    float right = texture(DepthSampler, texCoord + vec2(pixel.x, 0.0)).r;
    float down = texture(DepthSampler, texCoord - vec2(0.0, pixel.y)).r;
    float up = texture(DepthSampler, texCoord + vec2(0.0, pixel.y)).r;
    // Perspective depth is affine on a plane: preserve creases and silhouettes, not texture edges.
    float curvature = (abs(left + right - 2.0 * depth) + abs(down + up - 2.0 * depth)) / max(1.0 - depth, 0.00002);
    float edge = depth >= 0.999999 ? 0.0 : smoothstep(0.0018, 0.012, curvature);
    fragColor = vec4(edge, edge, edge, 1.0);
}
