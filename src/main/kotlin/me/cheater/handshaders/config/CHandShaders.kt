package me.cheater.handshaders.config

import me.cheater.handshaders.gui.Setting
import me.cheater.handshaders.gui.Settings
import me.cheater.handshaders.gui.Tab
import me.cheater.handshaders.utils.HandShaderImageLoader
import net.minecraft.Util
import java.awt.Color

@Tab("HandShaders")
object CHandShaders {
    const val NO_IMAGE = "No image"

    @Setting("Enabled")
    var enabled = false

    @Setting("Debug Logging", sameLineBefore = true)
    var debugLogging = false

    @Setting("Render Outline", lineBefore = true)
    var outlineEnabled = true

    @Setting("Outline Color")
    var outlineColor = Color(0, 255, 255, 255)

    @Setting("Dual Shader Mode", lineBefore = true)
    var dualShaderMode = false

    @Setting("Main Shader", combo = ["None", "Solid", "Chroma", "Smoke", "Glow", "Glass"])
    var mainShader = 1

    @Setting("Main Strength", min = 0.0, max = 1.0)
    var mainShaderStrength = 1.0f

    @Setting("Shader 1", lineBefore = true, combo = ["None", "Solid", "Chroma", "Smoke", "Glow", "Glass"])
    var shader1 = 4

    @Setting("Shader 1 Strength", min = 0.0, max = 1.0)
    var shader1Strength = 1.0f

    @Setting("Shader 2", combo = ["None", "Solid", "Chroma", "Smoke", "Glow", "Glass"])
    var shader2 = 3

    @Setting("Shader 2 Strength", min = 0.0, max = 1.0)
    var shader2Strength = 1.0f

    @Setting("Blend Mode", combo = ["Mix", "Add", "Multiply", "Screen", "Overlay"])
    var blendMode = 1

    @Setting("Blend Intensity", min = 0.0, max = 1.0)
    var blendIntensity = 1.0f

    @Setting("Solid Color 1", lineBefore = true)
    var solidColor1 = Color(0xE9, 0x1E, 0x63, 0xFF)

    @Setting("Solid Color 2")
    var solidColor2 = Color(0x9B, 0x59, 0xB6, 0xFF)

    @Setting("Gradient Speed", min = 0.1, max = 1.0)
    var gradientSpeed = 0.5f

    @Setting("Chroma Speed", lineBefore = true, min = 0.05, max = 1.0)
    var chromaSpeed = 0.3f

    @Setting("Chroma Saturation", min = 0.1, max = 1.0)
    var chromaSaturation = 0.5f

    @Setting("Chroma Brightness", min = 0.5, max = 1.5)
    var chromaBrightness = 1.0f

    @Setting("Smoke Color", lineBefore = true)
    var smokeColor = Color(0x95, 0xA5, 0xA6, 0xFF)

    @Setting("Smoke Intensity", min = 0.1, max = 3.0)
    var smokeIntensity = 1.0f

    @Setting("Smoke Speed", min = 0.1, max = 2.0)
    var smokeSpeed = 0.5f

    @Setting("Fill Glow", lineBefore = true)
    var fillGlow = true

    @Setting("Glow Radius", min = 0.01, max = 0.2)
    var glowRadius = 0.04f

    @Setting("Glow Power", min = 0.1, max = 2.0)
    var glowPower = 0.75f

    @Setting("Glow Color")
    var glowColor = Color(0x34, 0x98, 0xDB, 0xFF)

    @Setting("Glow Dispersion", min = 1.0, max = 10.0)
    var glowDispersion = 5.0f

    @Setting("Glass Blur Size", lineBefore = true, min = 5.0, max = 200.0)
    var glassBlurSize = 50.0f

    @Setting("Glass Quality", min = 3.0, max = 20.0)
    var glassQuality = 10.0f

    @Setting("Glass Direction", min = 4.0, max = 16.0)
    var glassDirection = 10.0f

    @Setting("Glass Refraction", min = 0.0, max = 3.0)
    var glassRefraction = 1.0f

    @Setting("Glass Brightness", min = 0.5, max = 4.0)
    var glassBrightness = 1.1f

    @Setting("Glass Chromatic")
    var glassChromatic = true

    @Setting("Glass Distortion", sameLineBefore = true)
    var glassDistortion = false

    @Setting("Glass Hide Hand", sameLineBefore = true)
    var glassHideHand = true

    @Setting("Background Blur", min = 10.0, max = 100.0)
    var glassBackgroundBlur = 70.0f

    @Setting("Image Folder", lineBefore = true)
    var imageFolder: () -> Unit = {
        Util.getPlatform().openPath(HandShaderImageLoader.imagesPath)
    }

    @Setting("Reload Images", sameLineBefore = true)
    var reloadImages: () -> Unit = {
        HandShaderImageLoader.reload()
        Settings.validateImageSelection()
        Settings.saveData()
    }

    @Setting("Image Selection")
    var imageSelection = NO_IMAGE

    @Setting("Image Alpha", min = 0.0, max = 255.0)
    var imageAlpha = 0
}
