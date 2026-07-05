#version 300 es
precision highp float;

// ── Passthrough Vertex Shader ─────────────────────────────────────────
// Simple fullscreen quad vertex shader for GPU processing pipeline.
// Transforms aPosition to clip space and passes aTexCoord through
// as vTexCoord for fragment shader consumption.

// Vertex attributes from VBO (interleaved: pos.x, pos.y, uv.x, uv.y)
in vec2 aPosition;   // Clip-space position: [-1, 1]
in vec2 aTexCoord;   // Texture coordinate: [0, 1]

// Passed to fragment shader
out vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
