package me.cheater.handshaders.features

import me.cheater.handshaders.gui.Settings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback

object HandShaderCommands {
    private var openRequested = false
    private var tickRegistered = false

    fun register() {
        if (!tickRegistered) {
            ClientTickEvents.END_CLIENT_TICK.register { client ->
                if (openRequested) {
                    openRequested = false
                    Settings.open()
                }
            }
            tickRegistered = true
        }

        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, _ ->
            dispatcher.register(
                literal("handshaders").executes {
                    openRequested = true
                    1
                }
            )
        })
    }
}
