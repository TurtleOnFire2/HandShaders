package me.cheater.handshaders.features

import com.mojang.blaze3d.framegraph.FrameGraphBuilder
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import me.cheater.handshaders.HandShadersClient
import me.cheater.handshaders.config.HandShaderConfig
import me.cheater.handshaders.mixin.accessor.GameRendererAccessor
import me.cheater.handshaders.mixin.accessor.LevelRendererAccessor
import me.cheater.handshaders.utils.HandShaderCompositeUtil
import me.cheater.handshaders.utils.HandShaderImageLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.client.renderer.PostChain
import net.minecraft.resources.ResourceLocation

object HandShaderRenderer {
    private val entityOutlinePostChainId: ResourceLocation = ResourceLocation.withDefaultNamespace("entity_outline")
    private val cleanupPostChainId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(HandShadersClient.MOD_ID, "hand_outline_cleanup")

    private var handOutlineActive = false
    private var frameIndex = 0L
    private var overrideCount = 0
    private var itemOverrideCount = 0
    private var modelOverrideCount = 0
    private var modelPartOverrideCount = 0
    private var lastDebugLogMs = 0L

    fun register() {}

    @JvmStatic
    fun captureBeforeHandRender() {
        if (!HandShaderConfig.enabled) return

        frameIndex++
        overrideCount = 0
        itemOverrideCount = 0
        modelOverrideCount = 0
        modelPartOverrideCount = 0

        val mc = Minecraft.getInstance()
        if ((mc.levelRenderer as LevelRendererAccessor).backingEntityOutlineTarget == null) {
            mc.levelRenderer.initOutline()
        }

        val outlineTarget = (mc.levelRenderer as LevelRendererAccessor).backingEntityOutlineTarget
        if (outlineTarget == null) {
            debugLog("capture skipped: backingEntityOutlineTarget=null")
            return
        }

        val encoder = RenderSystem.getDevice().createCommandEncoder()
        if (outlineTarget.useDepth) {
            encoder.clearColorAndDepthTextures(outlineTarget.colorTexture, 0, outlineTarget.depthTexture, 1.0)
        } else {
            encoder.clearColorTexture(outlineTarget.colorTexture, 0)
        }

        handOutlineActive = true
        debugLog("capture ok: frame=$frameIndex target=${outlineTarget.width}x${outlineTarget.height} useDepth=${outlineTarget.useDepth}")
    }

    @JvmStatic
    fun applyAfterHandRender() {
        if (!handOutlineActive) {
            debugLog("apply skipped: handOutlineActive=false")
            return
        }

        val mc = Minecraft.getInstance()
        mc.renderBuffers().outlineBufferSource().endOutlineBatch()
        val outlineTarget = (mc.levelRenderer as LevelRendererAccessor).backingEntityOutlineTarget
        val cleanupPostChain = mc.shaderManager.getPostChain(cleanupPostChainId, LevelTargetBundle.OUTLINE_TARGETS)
        val postChain = mc.shaderManager.getPostChain(entityOutlinePostChainId, LevelTargetBundle.OUTLINE_TARGETS)

        if (outlineTarget != null) {
            cleanupPostChain?.let { executeOutlinePostChain(it, mc, outlineTarget) }

            val imageLocation = HandShaderImageLoader.getImageResourceLocation(HandShaderConfig.imageName)
            if (HandShaderConfig.fillAlpha > 0 || (HandShaderConfig.imageAlpha > 0 && imageLocation != null)) {
                HandShaderCompositeUtil.composite(
                    mainTarget = mc.mainRenderTarget,
                    maskTarget = outlineTarget,
                    color = HandShaderConfig.outlineColor(),
                    fillAlpha = HandShaderConfig.fillAlpha,
                    imageAlpha = HandShaderConfig.imageAlpha,
                    imageTexture = imageLocation
                )
            }

            if (postChain != null) {
                executeOutlinePostChain(postChain, mc, outlineTarget)
                debugLog(
                    "postChain ok: frame=$frameIndex cleanup=${cleanupPostChain != null} " +
                        "imageName='${HandShaderConfig.imageName}' image=${imageLocation != null} " +
                        "fillAlpha=${HandShaderConfig.fillAlpha} imageAlpha=${HandShaderConfig.imageAlpha}"
                )
            } else {
                mc.levelRenderer.doEntityOutline()
                debugLog("postChain missing: outlineTarget=true postChain=false")
            }
        } else {
            debugLog("postChain missing: outlineTarget=false postChain=${postChain != null}")
        }

        debugLog(
            "apply ok: frame=$frameIndex overrides=$overrideCount items=$itemOverrideCount models=$modelOverrideCount modelParts=$modelPartOverrideCount"
        )
        handOutlineActive = false
    }

    private fun executeOutlinePostChain(postChain: PostChain, mc: Minecraft, outlineTarget: RenderTarget) {
        val frameGraphBuilder = FrameGraphBuilder()
        val targets = LevelTargetBundle()
        targets.main = frameGraphBuilder.importExternal("main", mc.mainRenderTarget)
        targets.entityOutline = frameGraphBuilder.importExternal("entity_outline", outlineTarget)
        postChain.addToFrame(frameGraphBuilder, mc.mainRenderTarget.width, mc.mainRenderTarget.height, targets)
        frameGraphBuilder.execute((mc.gameRenderer as GameRendererAccessor).resourcePool)
    }

    @JvmStatic
    fun overrideOutlineColor(existingColor: Int): Int {
        if (!handOutlineActive || !HandShaderConfig.enabled) return existingColor
        overrideCount++
        return HandShaderConfig.outlineColor().rgb
    }

    @JvmStatic
    fun isHandOutlineRoutingActive(): Boolean {
        return handOutlineActive && HandShaderConfig.enabled
    }

    @JvmStatic
    fun markItemOverride() {
        if (!handOutlineActive || !HandShaderConfig.enabled) return
        itemOverrideCount++
    }

    @JvmStatic
    fun markModelOverride() {
        if (!handOutlineActive || !HandShaderConfig.enabled) return
        modelOverrideCount++
    }

    @JvmStatic
    fun markModelPartOverride() {
        if (!handOutlineActive || !HandShaderConfig.enabled) return
        modelPartOverrideCount++
    }

    private fun debugLog(message: String) {
        if (!HandShaderConfig.debugLogging) return

        val now = System.currentTimeMillis()
        if (now - lastDebugLogMs < 500L && !message.contains("null") && !message.contains("skipped")) return
        lastDebugLogMs = now
        HandShadersClient.LOGGER.info("[HandShaders] {}", message)
    }
}
