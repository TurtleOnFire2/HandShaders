#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;

    float maxAlpha = 0.0;
    vec3 color = texture(InSampler, texCoord).rgb;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec4 sampleColor = texture(InSampler, texCoord + vec2(float(x), float(y)) * oneTexel);
            maxAlpha = max(maxAlpha, sampleColor.a);
            if (sampleColor.a > maxAlpha - 0.0001) {
                color = sampleColor.rgb;
            }
        }
    }

    float solidAlpha = step(0.001, maxAlpha);
    fragColor = vec4(color * solidAlpha, solidAlpha);
}
