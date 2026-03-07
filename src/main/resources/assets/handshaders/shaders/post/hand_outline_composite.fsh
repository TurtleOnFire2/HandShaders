#version 330

uniform sampler2D MainSampler;
uniform sampler2D MaskSampler;
uniform sampler2D BackgroundSampler;
uniform sampler2D ImageSampler;

layout(std140) uniform CompositeConfig {
    vec4 Meta0;
    vec4 Modes0;
    vec4 Blend0;
    vec4 Image0;
    vec4 OutlineColor;
    vec4 SolidColor1;
    vec4 SolidColor2;
    vec4 SolidConfig;
    vec4 ChromaConfig;
    vec4 SmokeColor;
    vec4 SmokeConfig;
    vec4 GlowColor;
    vec4 GlowConfig;
    vec4 GlassConfig0;
    vec4 GlassConfig1;
    vec4 GlassBackground;
};

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;
const int MODE_NONE = 0;
const int MODE_SOLID = 1;
const int MODE_CHROMA = 2;
const int MODE_SMOKE = 3;
const int MODE_GLOW = 4;
const int MODE_GLASS = 5;

const int BLEND_MIX = 0;
const int BLEND_ADD = 1;
const int BLEND_MULTIPLY = 2;
const int BLEND_SCREEN = 3;
const int BLEND_OVERLAY = 4;

float currentTime() {
    return Meta0.x;
}

vec2 resolution() {
    return vec2(max(Meta0.y, 1.0), max(Meta0.z, 1.0));
}

vec2 texelSize() {
    return 1.0 / resolution();
}

float maskAt(vec2 uv) {
    return step(0.5, texture(MaskSampler, clamp(uv, 0.0, 1.0)).a);
}

vec2 maskLocalUv(vec2 uv) {
    vec2 texel = texelSize();
    float left = 0.0;
    float right = 0.0;
    float up = 0.0;
    float down = 0.0;

    for (int i = 1; i <= 192; i++) {
        if (maskAt(uv - vec2(float(i), 0.0) * texel) < 0.5) break;
        left += 1.0;
    }

    for (int i = 1; i <= 192; i++) {
        if (maskAt(uv + vec2(float(i), 0.0) * texel) < 0.5) break;
        right += 1.0;
    }

    for (int i = 1; i <= 192; i++) {
        if (maskAt(uv - vec2(0.0, float(i)) * texel) < 0.5) break;
        up += 1.0;
    }

    for (int i = 1; i <= 192; i++) {
        if (maskAt(uv + vec2(0.0, float(i)) * texel) < 0.5) break;
        down += 1.0;
    }

    float width = max(left + right, 1.0);
    float height = max(up + down, 1.0);
    return vec2(left / width, up / height);
}

float maxMaskInRadius(vec2 uv, int radius) {
    float found = 0.0;
    vec2 texel = texelSize();
    for (int y = -10; y <= 10; y++) {
        for (int x = -10; x <= 10; x++) {
            if (abs(x) > radius || abs(y) > radius) continue;
            found = max(found, maskAt(uv + vec2(x, y) * texel));
        }
    }
    return found;
}

float minMaskInRadius(vec2 uv, int radius) {
    float found = 1.0;
    vec2 texel = texelSize();
    for (int y = -10; y <= 10; y++) {
        for (int x = -10; x <= 10; x++) {
            if (abs(x) > radius || abs(y) > radius) continue;
            found = min(found, maskAt(uv + vec2(x, y) * texel));
        }
    }
    return found;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 createVerticalGradient(vec2 coord, vec3 color1, vec3 color2, float t) {
    float factor = sin((coord.y + t) * PI * 2.0) * 0.5 + 0.5;
    return mix(color1, color2, factor);
}

vec4 liquidGlassBlur(sampler2D tex, vec2 uv, float dir, float qual, float size) {
    float directions = max(dir, 1.0);
    float quality = max(qual, 1.0);
    vec2 radius = texelSize() * size;
    vec4 color = texture(tex, uv);
    float total = 1.0;

    for (float d = 0.0; d < TAU; d += TAU / directions) {
        for (float i = 1.0 / quality; i <= 1.0; i += 1.0 / quality) {
            vec2 offset = vec2(cos(d), sin(d)) * radius * i;
            color += texture(tex, uv + offset);
            total += 1.0;
        }
    }

    return color / total;
}

vec4 matteBlur(sampler2D tex, vec2 uv, float blurRadius) {
    vec2 radius = blurRadius / resolution();
    vec4 blur = texture(tex, uv);
    float stepSize = TAU / 16.0;

    for (float d = 0.0; d < TAU; d += stepSize) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            blur += texture(tex, uv + vec2(cos(d), sin(d)) * radius * i);
        }
    }

    return blur / 81.0;
}

vec2 glassDistortion(vec2 uv, float strength) {
    float t = currentTime() * 0.35;
    float n1 = fract(sin(dot(uv + vec2(t, t * 0.7), vec2(12.9898, 78.233))) * 43758.5453);
    float n2 = fract(sin(dot(uv.yx + vec2(t * 0.5, t), vec2(39.3468, 11.135))) * 24634.6345);
    return (vec2(n1, n2) - 0.5) * 0.012 * strength;
}

vec3 blendAdd(vec3 base, vec3 blend) {
    return min(base + blend, vec3(1.0));
}

vec3 blendMultiply(vec3 base, vec3 blend) {
    return base * blend;
}

vec3 blendScreen(vec3 base, vec3 blend) {
    return vec3(1.0) - (vec3(1.0) - base) * (vec3(1.0) - blend);
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    vec3 result;
    result.r = base.r < 0.5 ? 2.0 * base.r * blend.r : 1.0 - 2.0 * (1.0 - base.r) * (1.0 - blend.r);
    result.g = base.g < 0.5 ? 2.0 * base.g * blend.g : 1.0 - 2.0 * (1.0 - base.g) * (1.0 - blend.g);
    result.b = base.b < 0.5 ? 2.0 * base.b * blend.b : 1.0 - 2.0 * (1.0 - base.b) * (1.0 - blend.b);
    return result;
}

vec3 renderSolid(vec3 originalColor, vec2 uv, float mask, float strength) {
    if (mask < 0.001) return originalColor;
    vec3 gradientColor = createVerticalGradient(uv, SolidColor1.rgb, SolidColor2.rgb, currentTime() * SolidConfig.x);
    float brightness = 0.85 + sin(currentTime() * 6.28318) * 0.15;
    float alpha = max(SolidColor1.a, SolidColor2.a) * strength;
    return mix(originalColor, gradientColor * brightness, alpha * mask);
}

vec3 renderChroma(vec3 originalColor, vec2 uv, float mask, float strength) {
    if (mask < 0.001) return originalColor;
    float hue = fract(uv.y * 0.5 + uv.x * 0.3 + currentTime() * ChromaConfig.x);
    hue += sin(uv.y * 10.0 + currentTime() * 3.0) * 0.05;
    vec3 chromaColor = hsv2rgb(vec3(hue, ChromaConfig.y, ChromaConfig.z));
    chromaColor *= 0.9 + sin(currentTime() * 4.0) * 0.1;
    return mix(originalColor, chromaColor, 0.85 * strength * mask);
}

vec3 renderSmoke(vec3 originalColor, vec2 uv, float mask, float strength) {
    if (mask < 0.001) return originalColor;
    vec2 smokeUv = (2.0 * uv - 1.0) * resolution().y / min(resolution().x, resolution().y);
    float effectTime = currentTime() * SmokeConfig.y;
    for (float i = 1.0; i < 10.0; i++) {
        smokeUv.x += 0.6 / i * cos(i * 2.5 * smokeUv.y + effectTime);
        smokeUv.y += 0.6 / i * cos(i * 1.5 * smokeUv.x + effectTime);
    }
    float smokePattern = 0.1 / abs(sin(effectTime - smokeUv.y - smokeUv.x));
    vec3 col = SmokeColor.rgb * smokePattern * SmokeConfig.x;
    return mix(originalColor, col, 0.85 * strength * mask);
}

vec3 renderGlow(vec3 originalColor, vec2 uv, float mask, float strength) {
    int disperseRadius = clamp(int(round(GlowConfig.w)), 1, 10);
    float nearbyMask = maxMaskInRadius(uv, disperseRadius);
    if (mask < 0.001 && nearbyMask < 0.001) return originalColor;

    float edgeStrength = 0.0;
    float outwardGlow = 0.0;
    vec2 texel = texelSize();

    for (int y = -10; y <= 10; y++) {
        for (int x = -10; x <= 10; x++) {
            if (abs(x) > disperseRadius || abs(y) > disperseRadius || (x == 0 && y == 0)) continue;
            float neighborMask = maskAt(uv + vec2(x, y) * texel);
            float dist = length(vec2(x, y));
            if (neighborMask < 0.001 && mask > 0.001) {
                edgeStrength += 1.0 / (dist + 0.1);
            }
            if (mask < 0.001 && neighborMask > 0.001) {
                float falloff = 1.0 - (dist / float(disperseRadius));
                outwardGlow += pow(max(falloff, 0.0), 2.5) * 0.2;
            }
        }
    }

    edgeStrength = clamp(edgeStrength * 0.15, 0.0, 1.0);
    outwardGlow = clamp(outwardGlow, 0.0, 1.0);
    float pulse = 0.9 + sin(currentTime() * 2.0) * 0.1;

    if (mask < 0.001 && outwardGlow > 0.01) {
        return originalColor + GlowColor.rgb * outwardGlow * GlowConfig.z * pulse * GlowConfig.y * 10.0 * strength;
    }

    if (mask < 0.001) return originalColor;

    if (GlowConfig.x > 0.5) {
        vec3 handGlow = GlowColor.rgb * (GlowConfig.y * 10.0);
        vec3 edgeGlow = GlowColor.rgb * edgeStrength * GlowConfig.z * pulse * 2.5;
        return mix(originalColor, handGlow + edgeGlow, 0.75 * strength);
    }

    vec3 edgeGlow = GlowColor.rgb * edgeStrength * GlowConfig.z * pulse * GlowConfig.y * 25.0;
    return originalColor + edgeGlow * strength;
}

vec3 renderGlass(vec3 originalColor, vec3 backgroundColor, vec2 uv, float mask, float strength) {
    if (mask < 0.001) return originalColor;

    bool enableChromatic = GlassConfig1.y > 0.5;
    bool enableDistortion = GlassConfig1.z > 0.5;
    bool hideHand = GlassConfig1.w > 0.5;
    vec2 distortedUV = uv;
    if (enableDistortion) {
        distortedUV += glassDistortion(uv, GlassConfig0.w);
    }
    distortedUV = clamp(distortedUV, 0.0, 1.0);

    vec3 refractedBackground = texture(BackgroundSampler, distortedUV).rgb;
    vec3 glassColor;

    if (GlassBackground.x > 0.1) {
        glassColor = matteBlur(BackgroundSampler, distortedUV, GlassBackground.x).rgb;
    } else {
        glassColor = liquidGlassBlur(BackgroundSampler, distortedUV, GlassConfig0.z, GlassConfig0.y, GlassConfig0.x).rgb;
    }

    if (enableChromatic) {
        float aberration = 0.003 * GlassConfig0.w;
        float r = liquidGlassBlur(BackgroundSampler, distortedUV + vec2(aberration, 0.0), GlassConfig0.z, GlassConfig0.y, GlassConfig0.x * 0.5).r;
        float b = liquidGlassBlur(BackgroundSampler, distortedUV - vec2(aberration, 0.0), GlassConfig0.z, GlassConfig0.y, GlassConfig0.x * 0.5).b;
        glassColor.r = r;
        glassColor.b = b;
    }
    float glassBrightness = max(GlassConfig1.x, 0.0);
    glassColor *= glassBrightness;
    float whiteBoost = clamp((glassBrightness - 1.0) / 3.0, 0.0, 1.0);
    glassColor = mix(glassColor, vec3(1.0), whiteBoost * 0.55);
    glassColor = clamp(glassColor, 0.0, 1.0);

    float edgeStrength = 0.0;
    vec2 texel = texelSize();
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            if (x == 0 && y == 0) continue;
            float neighborMask = maskAt(uv + vec2(x, y) * texel);
            if (neighborMask < 0.001) {
                float dist = length(vec2(x, y));
                edgeStrength += 1.0 / (dist + 0.1);
            }
        }
    }

    edgeStrength = clamp(edgeStrength * 0.1, 0.0, 1.0);
    glassColor += vec3(1.0) * edgeStrength * 0.3;

    vec3 baseColor = hideHand ? refractedBackground : mix(originalColor, refractedBackground, 0.5);
    vec3 effectColor = mix(baseColor, glassColor, 0.8);
    return mix(originalColor, effectColor, strength * mask);
}

vec3 renderMode(int mode, vec3 originalColor, vec3 backgroundColor, vec2 uv, float mask, float strength) {
    if (mode == MODE_NONE || strength <= 0.0) return originalColor;
    if (mode == MODE_SOLID) return renderSolid(originalColor, uv, mask, strength);
    if (mode == MODE_CHROMA) return renderChroma(originalColor, uv, mask, strength);
    if (mode == MODE_SMOKE) return renderSmoke(originalColor, uv, mask, strength);
    if (mode == MODE_GLOW) return renderGlow(originalColor, uv, mask, strength);
    if (mode == MODE_GLASS) return renderGlass(originalColor, backgroundColor, uv, mask, strength);
    return originalColor;
}

vec3 blendDualResults(vec3 color1, vec3 color2) {
    float intensity = clamp(Blend0.y, 0.0, 1.0);
    return mix(color1, color2, intensity);
}

void main() {
    vec4 mainColor = texture(MainSampler, texCoord);
    vec3 backgroundColor = texture(BackgroundSampler, texCoord).rgb;
    float mask = maskAt(texCoord);
    float maskMax = maxMaskInRadius(texCoord, 1);
    float maskMin = minMaskInRadius(texCoord, 1);
    float outlineMask = (1.0 - mask) * clamp(maskMax - maskMin, 0.0, 1.0);

    bool dual = Modes0.y > 0.5;
    int mainMode = int(round(Modes0.x));
    int shader1Mode = int(round(Modes0.z));
    int shader2Mode = int(round(Modes0.w));
    bool glowActive = (!dual && mainMode == MODE_GLOW) || (dual && (shader1Mode == MODE_GLOW || shader2Mode == MODE_GLOW));
    bool hasImage = Image0.y > 0.5 && Image0.x > 0.0;
    bool hasOutline = OutlineColor.a > 0.001;

    if (!glowActive && mask < 0.001 && outlineMask < 0.001 && !hasImage) {
        fragColor = mainColor;
        return;
    }

    vec3 finalColor = mainColor.rgb;
    if (dual) {
        vec3 shader1Color = renderMode(shader1Mode, mainColor.rgb, backgroundColor, texCoord, mask, Blend0.z);
        vec3 shader2Color = renderMode(shader2Mode, mainColor.rgb, backgroundColor, texCoord, mask, Blend0.w);
        bool shader1Changed = length(shader1Color - mainColor.rgb) > 0.0001;
        bool shader2Changed = length(shader2Color - mainColor.rgb) > 0.0001;

        if (shader1Changed && shader2Changed) {
            finalColor = blendDualResults(shader1Color, shader2Color);
        } else if (shader1Changed) {
            finalColor = shader1Color;
        } else if (shader2Changed) {
            finalColor = shader2Color;
        }
    } else {
        finalColor = renderMode(mainMode, mainColor.rgb, backgroundColor, texCoord, mask, Image0.z);
    }

    if (hasImage && mask > 0.0) {
        vec4 imageColor = texture(ImageSampler, texCoord);
        float imageBlend = Image0.x * imageColor.a * mask;
        finalColor = mix(finalColor, imageColor.rgb, imageBlend);
    }

    if (hasOutline && outlineMask > 0.0) {
        finalColor = mix(finalColor, OutlineColor.rgb, outlineMask * OutlineColor.a);
    }

    fragColor = vec4(finalColor, mainColor.a);
}
