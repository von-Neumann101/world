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
    // Keep significant depth discontinuities, not shallow face creases or subpixel details.
    float curvature = (abs(left + right - 2.0 * depth) + abs(down + up - 2.0 * depth)) / max(1.0 - depth, 0.00002);
    // Only the foreground side owns a contour: no second parallel line on the background.
    float outwardStep = (max(max(left, right), max(down, up)) - depth) / max(1.0 - depth, 0.00002);
    float edge = depth >= 0.999999 ? 0.0 : smoothstep(0.014, 0.06, curvature) * smoothstep(0.012, 0.04, outwardStep);
    // Measure detail density BEFORE simplifying lines; otherwise sparse surviving leaf dots
    // evade the density filter and become brighter when other outlines are removed.
    float detail = depth >= 0.999999 ? 0.0 : smoothstep(0.0018, 0.012, curvature);
    fragColor = vec4(edge, detail, 0.0, 1.0);
}
