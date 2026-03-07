package me.cheater.handshaders

import me.cheater.handshaders.config.HandShaderConfig
import me.cheater.handshaders.features.HandShaderCommands
import me.cheater.handshaders.features.HandShaderRenderer
import me.cheater.handshaders.gui.Settings
import me.cheater.handshaders.utils.HandShaderImageLoader
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory

object HandShadersClient : ClientModInitializer {
    const val MOD_ID = "handshaders"
    val LOGGER = LoggerFactory.getLogger("handshaders")
    val mc: Minecraft
        get() = Minecraft.getInstance()

    override fun onInitializeClient() {
        HandShaderConfig.load()
        HandShaderImageLoader.reload()
        Settings.validateImageSelection()
        HandShaderCommands.register()
        HandShaderRenderer.register()
    }
}
