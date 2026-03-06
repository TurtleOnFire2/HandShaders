#version 330

uniform sampler2D MainSampler;
uniform sampler2D MaskSampler;
uniform sampler2D ImageSampler;

layout(std140) uniform CompositeConfig {
    vec4 FillColor;
    vec4 ImageConfig;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 mainColor = texture(MainSampler, texCoord);
    float mask = texture(MaskSampler, texCoord).a;

    vec3 outColor = mainColor.rgb;

    if (FillColor.a > 0.0) {
        outColor = mix(outColor, FillColor.rgb, FillColor.a * mask);
    }

    if (ImageConfig.y > 0.5 && ImageConfig.x > 0.0) {
        vec4 imageColor = texture(ImageSampler, texCoord);
        float imageBlend = ImageConfig.x * imageColor.a * mask;
        outColor = mix(outColor, imageColor.rgb, imageBlend);
    }

    fragColor = vec4(outColor, mainColor.a);
}
