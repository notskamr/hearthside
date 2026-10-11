#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(push_constant) uniform ViewConstants {
    vec4 Dst;
    vec4 Src;
    vec4 Params; // texel width, texel height, alpha, blur radius in texels
} view;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 color;
    float radius = view.Params.w;
    if (radius > 0.0) {
        // A frame waiting for its new size: a soft box blur, 5x5 filtered taps spread over +-radius texels.
        vec2 spacing = view.Params.xy * radius * 0.5;
        color = vec3(0.0);
        for (int y = -2; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                color += texture(InSampler, texCoord + vec2(x, y) * spacing).rgb;
            }
        }
        color /= 25.0;
    } else {
        color = texture(InSampler, texCoord).rgb;
    }
    // A view is an opaque picture; its alpha fades it over the divider (blended as premultiplied alpha).
    float alpha = view.Params.z;
    fragColor = vec4(color * alpha, alpha);
}
