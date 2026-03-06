package me.cheater.handshaders.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path

object HandShaderConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path: Path = FabricLoader.getInstance().configDir.resolve("handshaders.json")
    private var data = Data()

    data class Rgba(
        var r: Int = 0,
        var g: Int = 255,
        var b: Int = 255,
        var a: Int = 255
    )

    data class Data(
        var enabled: Boolean = false,
        var outlineColor: Rgba = Rgba(),
        var fillAlpha: Int = 0,
        var imageName: String = "",
        var imageAlpha: Int = 0,
        var debugLogging: Boolean = false
    )

    var enabled: Boolean
        get() = data.enabled
        set(value) {
            data.enabled = value
            save()
        }

    var fillAlpha: Int
        get() = data.fillAlpha
        set(value) {
            data.fillAlpha = value.coerceIn(0, 255)
            save()
        }

    var imageName: String
        get() = data.imageName
        set(value) {
            data.imageName = value.trim()
            save()
        }

    var imageAlpha: Int
        get() = data.imageAlpha
        set(value) {
            data.imageAlpha = value.coerceIn(0, 255)
            save()
        }

    var debugLogging: Boolean
        get() = data.debugLogging
        set(value) {
            data.debugLogging = value
            save()
        }

    fun load() {
        if (!Files.exists(path)) {
            save()
            return
        }

        runCatching {
            Files.newBufferedReader(path).use { reader ->
                data = gson.fromJson(reader, Data::class.java) ?: Data()
            }
        }.onFailure {
            data = Data()
            save()
        }
    }

    fun save() {
        val parent = path.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }

        Files.newBufferedWriter(path).use { writer ->
            gson.toJson(data, writer)
        }
    }

    fun outlineColor(): Color {
        val color = data.outlineColor
        return Color(
            color.r.coerceIn(0, 255),
            color.g.coerceIn(0, 255),
            color.b.coerceIn(0, 255),
            color.a.coerceIn(0, 255)
        )
    }

    fun setOutlineColor(color: Color) {
        data.outlineColor = Rgba(color.red, color.green, color.blue, color.alpha)
        save()
    }

    fun statusLine(): String {
        val color = outlineColor()
        val imageText = if (imageName.isBlank()) "<none>" else imageName
        return "enabled=$enabled color=${color.red},${color.green},${color.blue},${color.alpha} fillAlpha=$fillAlpha image=$imageText imageAlpha=$imageAlpha debug=$debugLogging"
    }
}
