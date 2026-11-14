#version 150
uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform sampler2D ConnectionSampler;
uniform mat4 InverseViewProjection;
uniform vec3 PlaneCenter;
uniform vec3 PlaneNormal;
uniform vec3 PlaneRight;
uniform vec2 HalfSize;
uniform float Strength;
in vec2 texCoord;
out vec4 fragColor;

vec3 unproject(float depth) {
    vec4 p=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz/p.w;
}
void main() {
    vec4 base=texture(SceneSampler,texCoord);
    fragColor=vec4(base.rgb,1.0);
    if(Strength<=0.0)return;
    vec4 eye=InverseViewProjection*vec4(0.0,0.0,1.0,0.0);
    vec3 origin=eye.xyz/eye.w;
    vec3 ray=normalize(unproject(0.75)-unproject(0.0));
    float facing=dot(ray,PlaneNormal);
    if(facing<=0.00001)return;
    float distance=dot(PlaneCenter-origin,PlaneNormal)/facing;
    if(distance<=0.0)return;
    vec3 point=origin+ray*distance;
    vec3 local=point-PlaneCenter;
    vec2 remaining=HalfSize-abs(vec2(dot(local,PlaneRight),local.y));
    float coverage=smoothstep(0.0,0.7,min(remaining.x,remaining.y))*Strength;
    if(coverage<=0.0)return;
    float depth=texture(DepthSampler,texCoord).r;
    if(depth<0.999999 && dot(unproject(depth)-origin,ray)<distance+0.02)return;
    // The destination is an independently rendered camera view, not a warped copy of this image.
    fragColor=vec4(mix(base.rgb,texture(ConnectionSampler,texCoord).rgb,coverage),1.0);
}
