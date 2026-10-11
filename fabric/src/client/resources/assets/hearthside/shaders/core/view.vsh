#version 330
#extension GL_ARB_separate_shader_objects : require

// One view on Hearthside's canvas (see ViewPipeline and ViewQuad). Pictures are stored bottom-up, as Minecraft stores
// every render target on both backends, while the rectangles below count from the top-left.
layout(push_constant) uniform ViewConstants {
    vec4 Dst;    // x0, y0, x1, y1 on the canvas, 0..1 from its top-left
    vec4 Src;    // u0, v0, u1, v1 in the picture, 0..1 from its top-left
    vec4 Params; // texel width, texel height, alpha, blur radius in texels
} view;

layout(location = 0) out vec2 texCoord;

void main() {
    // Two triangles, (0,0) (1,0) (0,1) and (0,1) (1,0) (1,1), with no vertex buffer; t runs from the top-left.
    int i = gl_VertexIndex;
    vec2 t = vec2(i == 1 || i == 4 || i == 5 ? 1.0 : 0.0, i == 2 || i == 3 || i == 5 ? 1.0 : 0.0);
    gl_Position = vec4(mix(view.Dst.x, view.Dst.z, t.x) * 2.0 - 1.0, 1.0 - mix(view.Dst.y, view.Dst.w, t.y) * 2.0, 0.0, 1.0);
    texCoord = vec2(mix(view.Src.x, view.Src.z, t.x), 1.0 - mix(view.Src.y, view.Src.w, t.y));
}
