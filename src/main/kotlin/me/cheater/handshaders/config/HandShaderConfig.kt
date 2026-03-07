package me.cheater.handshaders.config

import com.google.gson.Gson
import me.cheater.handshaders.gui.Settings
import net.fabricmc.loader.api.FabricLoader
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path

object HandShaderConfig {
    const val MODE_NONE = 0
    const val MODE_SOLID = 1
    const val MODE_CHROMA = 2
    const val MODE_SMOKE = 3
    const val MODE_GLOW = 4
    const val MODE_GLASS = 5

    const val BLEND_MIX = 0
    const val BLEND_ADD = 1
    const val BLEND_MULTIPLY = 2
    const val BLEND_SCREEN = 3
    const val BLEND_OVERLAY = 4

    private val gson = Gson()
    private val legacyPath: Path = FabricLoader.getInstance().configDir.resolve("handshaders.json")
    private val migratedPath: Path = FabricLoader.getInstance().configDir.resolve("handshaders/tabs/HandShaders.txt")

    data class LegacyRgba(
        var r: Int = 0,
        var g: Int = 255,
        var b: Int = 255,
        var a: Int = 255
    )

    data class LegacyData(
        var enabled: Boolean = false,
        var outlineColor: LegacyRgba = LegacyRgba(),
        var fillAlpha: Int = 0,
        var imageName: String = "",
        var imageAlpha: Int = 0,
        var debugLogging: Boolean = false
    )

    var enabled: Boolean
        get() = CHandShaders.enabled
        set(value) {
            CHandShaders.enabled = value
            save()
        }

    var debugLogging: Boolean
        get() = CHandShaders.debugLogging
        set(value) {
            CHandShaders.debugLogging = value
            save()
        }

    var imageName: String
        get() = CHandShaders.imageSelection.takeUnless { it == CHandShaders.NO_IMAGE }.orEmpty()
        set(value) {
            CHandShaders.imageSelection = value.trim().ifBlank { CHandShaders.NO_IMAGE }
            save()
        }

    var imageAlpha: Int
        get() = CHandShaders.imageAlpha.coerceIn(0, 255)
        set(value) {
            CHandShaders.imageAlpha = value.coerceIn(0, 255)
            save()
        }

    fun load() {
        Settings
        migrateLegacyConfig()
        normalize()
    }

    fun save() = Settings.saveData()

    fun outlineColor(): Color {
        val color = CHandShaders.outlineColor
        return Color(color.red, color.green, color.blue, color.alpha)
    }

    fun setOutlineColor(color: Color) {
        CHandShaders.outlineColor = Color(
            color.red.coerceIn(0, 255),
            color.green.coerceIn(0, 255),
            color.blue.coerceIn(0, 255),
            color.alpha.coerceIn(0, 255)
        )
        save()
    }

    fun renderOutline(): Boolean = CHandShaders.outlineEnabled
    fun dualShaderMode(): Boolean = CHandShaders.dualShaderMode

    fun mainShaderMode(): Int = CHandShaders.mainShader.coerceIn(MODE_NONE, MODE_GLASS)
    fun mainShaderStrength(): Float = CHandShaders.mainShaderStrength.coerceIn(0f, 1f)

    fun shader1Mode(): Int = CHandShaders.shader1.coerceIn(MODE_NONE, MODE_GLASS)
    fun shader1Strength(): Float = CHandShaders.shader1Strength.coerceIn(0f, 1f)

    fun shader2Mode(): Int = CHandShaders.shader2.coerceIn(MODE_NONE, MODE_GLASS)
    fun shader2Strength(): Float = CHandShaders.shader2Strength.coerceIn(0f, 1f)

    fun blendMode(): Int = CHandShaders.blendMode.coerceIn(BLEND_MIX, BLEND_OVERLAY)
    fun blendIntensity(): Float = CHandShaders.blendIntensity.coerceIn(0f, 1f)

    fun hasShaderEffect(): Boolean = if (dualShaderMode()) {
        shader1Mode() != MODE_NONE || shader2Mode() != MODE_NONE
    } else {
        mainShaderMode() != MODE_NONE
    }

    fun hasImageOverlay(): Boolean = imageAlpha > 0 && imageName.isNotBlank()
    fun shouldComposite(): Boolean = enabled && (hasShaderEffect() || hasImageOverlay())

    fun requiresBackgroundCapture(): Boolean {
        if (!enabled) return false
        return activeModes().any { usesGlassReplacement(it) }
    }

    fun shouldHideBaseHandForGlass(): Boolean = enabled && activeModes().any { usesGlassReplacement(it) }

    fun usesGlow(): Boolean = activeModes().any { it == MODE_GLOW }
    fun activeModes(): List<Int> = if (dualShaderMode()) listOf(shader1Mode(), shader2Mode()) else listOf(mainShaderMode())

    fun shaderName(mode: Int): String = when (mode.coerceIn(MODE_NONE, MODE_GLASS)) {
        MODE_NONE -> "None"
        MODE_SOLID -> "Solid"
        MODE_CHROMA -> "Chroma"
        MODE_SMOKE -> "Smoke"
        MODE_GLOW -> "Glow"
        MODE_GLASS -> "Glass"
        else -> "None"
    }

    fun statusLine(): String {
        val imageText = if (imageName.isBlank()) "<none>" else imageName
        val modeText = if (dualShaderMode()) {
            "dual=${shaderName(shader1Mode())}+${shaderName(shader2Mode())} blend=${blendMode()}"
        } else {
            "main=${shaderName(mainShaderMode())} strength=${"%.2f".format(mainShaderStrength())}"
        }
        return "enabled=$enabled outline=${renderOutline()} $modeText image=$imageText imageAlpha=$imageAlpha debug=$debugLogging"
    }

    private fun normalize() {
        CHandShaders.mainShader = mainShaderMode()
        CHandShaders.shader1 = shader1Mode()
        CHandShaders.shader2 = shader2Mode()
        CHandShaders.blendMode = blendMode()
        CHandShaders.mainShaderStrength = mainShaderStrength()
        CHandShaders.shader1Strength = shader1Strength()
        CHandShaders.shader2Strength = shader2Strength()
        CHandShaders.blendIntensity = blendIntensity()
        CHandShaders.imageAlpha = imageAlpha
        CHandShaders.imageSelection = imageName.ifBlank { CHandShaders.NO_IMAGE }
        CHandShaders.outlineColor = outlineColor()
        save()
    }

    private fun usesGlassReplacement(mode: Int): Boolean = mode == MODE_GLASS

    private fun migrateLegacyConfig() {
        if (!Files.exists(legacyPath) || Files.exists(migratedPath)) return

        runCatching {
            Files.newBufferedReader(legacyPath).use { reader ->
                val data = gson.fromJson(reader, LegacyData::class.java) ?: return
                val outline = Color(
                    data.outlineColor.r.coerceIn(0, 255),
                    data.outlineColor.g.coerceIn(0, 255),
                    data.outlineColor.b.coerceIn(0, 255),
                    data.outlineColor.a.coerceIn(0, 255)
                )

                enabled = data.enabled
                debugLogging = data.debugLogging
                imageName = data.imageName
                imageAlpha = data.imageAlpha
                CHandShaders.outlineEnabled = true
                setOutlineColor(outline)
                CHandShaders.solidColor1 = outline
                CHandShaders.solidColor2 = outline
                CHandShaders.mainShader = if (data.fillAlpha > 0) MODE_SOLID else MODE_NONE
                CHandShaders.mainShaderStrength = 1.0f
            }
        }
    }
}
