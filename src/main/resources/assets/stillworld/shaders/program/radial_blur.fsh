#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 Direction;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 step = Direction / InSize;
    float strength = texture(DiffuseSampler, texCoord - 2.0 * step).r
        + 4.0 * texture(DiffuseSampler, texCoord - step).r
        + 6.0 * texture(DiffuseSampler, texCoord).r
        + 4.0 * texture(DiffuseSampler, texCoord + step).r
        + texture(DiffuseSampler, texCoord + 2.0 * step).r;
    fragColor = vec4(vec3(strength / 16.0), 1.0);
}
