package me.cheater.handshaders.utils

import com.mojang.blaze3d.platform.NativeImage
import me.cheater.handshaders.HandShadersClient
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension

object HandShaderImageLoader {
    val imagesPath: Path = FabricLoader.getInstance().configDir.resolve("handshaders/images")

    private val loadedImages = ConcurrentHashMap<String, ImageData>()
    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    @Volatile
    private var watchStarted = false

    data class ImageData(
        val resourceLocation: ResourceLocation,
        val texture: DynamicTexture,
        val width: Int,
        val height: Int
    )

    private fun normalizeName(name: String): String =
        name.trim().removeSuffix(".png").removeSuffix(".PNG").lowercase()

    fun reload() {
        if (!imagesPath.exists()) {
            Files.createDirectories(imagesPath)
        }

        HandShadersClient.mc.execute {
            loadedImages.values.forEach { HandShadersClient.mc.textureManager.release(it.resourceLocation) }
            loadedImages.clear()

            Files.list(imagesPath).use { files ->
                files.filter { it.extension.lowercase() == "png" }.forEach(::loadImage)
            }
        }

        if (!watchStarted) {
            imagesPath.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )

            Thread(::watchLoop, "HandShaders-ImageLoader").apply {
                isDaemon = true
                start()
            }
            watchStarted = true
        }
    }

    private fun loadImage(path: Path, suffix: String = UUID.randomUUID().toString().replace("-", "").take(6)) {
        if (path.extension.lowercase() != "png") return

        val fileName = normalizeName(path.nameWithoutExtension)
        try {
            path.inputStream().use { input ->
                val nativeImage = NativeImage.read(input)
                val resourceLocation = ResourceLocation.fromNamespaceAndPath(
                    HandShadersClient.MOD_ID,
                    "hand_shader_${fileName.replace(" ", "_")}_$suffix"
                )

                val old = loadedImages.remove(fileName)
                old?.let { HandShadersClient.mc.textureManager.release(it.resourceLocation) }

                val texture = DynamicTexture({ resourceLocation.toString() }, nativeImage)
                HandShadersClient.mc.textureManager.register(resourceLocation, texture)
                loadedImages[fileName] = ImageData(resourceLocation, texture, nativeImage.width, nativeImage.height)
                HandShadersClient.LOGGER.info("Loaded hand shader image '{}'", path.fileName)
            }
        } catch (e: Exception) {
            HandShadersClient.LOGGER.warn("Failed to load hand shader image '{}': {}", path.fileName, e.message)
        }
    }

    private fun unloadImage(fileName: String) {
        val image = loadedImages.remove(normalizeName(fileName)) ?: return
        HandShadersClient.mc.execute {
            HandShadersClient.mc.textureManager.release(image.resourceLocation)
        }
    }

    private fun watchLoop() {
        while (true) {
            val key: WatchKey = try {
                watchService.take()
            } catch (_: InterruptedException) {
                return
            }

            for (event in key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue

                @Suppress("UNCHECKED_CAST")
                val pathEvent = event as WatchEvent<Path>
                val path = imagesPath.resolve(pathEvent.context())
                val fileName = normalizeName(path.fileName.toString().substringBeforeLast('.'))

                when (event.kind()) {
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY -> {
                        Thread.sleep(100)
                        if (path.exists() && path.extension.lowercase() == "png") {
                            HandShadersClient.mc.execute { loadImage(path) }
                        }
                    }

                    StandardWatchEventKinds.ENTRY_DELETE -> unloadImage(fileName)
                }
            }

            key.reset()
        }
    }

    fun getImageResourceLocation(name: String): ResourceLocation? = loadedImages[normalizeName(name)]?.resourceLocation

    fun getImageData(name: String): ImageData? = loadedImages[normalizeName(name)]

    fun getImageNames(): Array<String> =
        arrayOf("No image") + loadedImages.keys.sorted().toTypedArray()

    fun imageExists(name: String): Boolean = loadedImages.containsKey(normalizeName(name))
}
