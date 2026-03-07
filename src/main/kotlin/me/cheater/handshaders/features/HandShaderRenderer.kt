package me.cheater.handshaders.features

import com.mojang.blaze3d.framegraph.FrameGraphBuilder
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
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
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.resources.ResourceLocation

object HandShaderRenderer {
    private val cleanupPostChainId: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(HandShadersClient.MOD_ID, "hand_outline_cleanup")

    private var handOutlineActive = false
    private var frameIndex = 0L
    private var overrideCount = 0
    private var itemOverrideCount = 0
    private var modelOverrideCount = 0
    private var modelPartOverrideCount = 0
    private var lastDebugLogMs = 0L
    private var preHandTarget: TextureTarget? = null
    private var cleanHandTarget: TextureTarget? = null
    private var mainColorMaskSuppressed = false
    private var handCompositePending = false
    private var outlineBatchFlushed = false

    fun register() {}

    @JvmStatic
    fun captureBeforeHandRender() {
        if (!HandShaderConfig.enabled || (!HandShaderConfig.shouldComposite() && !HandShaderConfig.renderOutline())) return

        frameIndex++
        overrideCount = 0
        itemOverrideCount = 0
        modelOverrideCount = 0
        modelPartOverrideCount = 0
        outlineBatchFlushed = false

        val mc = Minecraft.getInstance()
        capturePreHandTarget(mc.mainRenderTarget)

        if ((mc.levelRenderer as LevelRendererAccessor).backingEntityOutlineTarget == null) {
            mc.levelRenderer.initOutline()
        }

        val outlineTarget = (mc.levelRenderer as LevelRendererAccessor).backingEntityOutlineTarget
        if (outlineTarget == null) {
            debugLog("capture skipped: backingEntityOutlineTarget=null")
            return
        }

        clearRenderTarget(outlineTarget)

        handOutlineActive = true
        handCompositePending = true
        debugLog("capture ok: frame=$frameIndex target=${outlineTarget.width}x${outlineTarget.height} useDepth=${outlineTarget.useDepth} bgCapture=${HandShaderConfig.requiresBackgroundCapture()}")
    }

    @JvmStatic
    fun selectHandSubmitCollector(defaultCollector: SubmitNodeCollector): SubmitNodeCollector {
        return defaultCollector
    }

    @JvmStatic
    fun suppressMainColorWritesIfNeeded() {
        if (!HandShaderConfig.shouldHideBaseHandForGlass()) return
    }

    @JvmStatic
    fun restoreMainColorWrites() {
        if (!mainColorMaskSuppressed) return
        GlStateManager._colorMask(true, true, true, true)
        mainColorMaskSuppressed = false
        debugLog("main color writes restored")
    }

    @JvmStatic
    fun finishHandSubmission() {
        if (!handCompositePending) {
            debugLog("finish skipped: handCompositePending=false")
            return
        }

        restoreMainColorWrites()
        flushOutlineBatchIfNeeded()
    }

    @JvmStatic
    fun applyAfterFeatureRender() {
        if (!handCompositePending) {
            return
        }

        val mc = Minecraft.getInstance()
        val glassReplacementActive = HandShaderConfig.shouldHideBaseHandForGlass()
        captureCleanHandTarget(mc.mainRenderTarget)
        flushOutlineBatchIfNeeded()
        if (glassReplacementActive) {
            preHandTarget?.let { copyColor(it, mc.mainRenderTarget) }
        } else {
            cleanHandTarget?.let { copyColor(it, mc.mainRenderTarget) }
        }
        val outlineTarget = (mc.levelRenderer as LevelRendererAccessor).backingEntityOutlineTarget
        val cleanupPostChain = mc.shaderManager.getPostChain(cleanupPostChainId, LevelTargetBundle.OUTLINE_TARGETS)

        if (outlineTarget != null) {
            cleanupPostChain?.let { executeOutlinePostChain(it, mc, outlineTarget) }

            val imageLocation = HandShaderImageLoader.getImageResourceLocation(HandShaderConfig.imageName)
            if (HandShaderConfig.shouldComposite() || HandShaderConfig.renderOutline()) {
                val sourceTarget = if (glassReplacementActive) {
                    cleanHandTarget ?: preHandTarget ?: mc.mainRenderTarget
                } else {
                    cleanHandTarget ?: mc.mainRenderTarget
                }
                HandShaderCompositeUtil.composite(
                    sourceTarget = sourceTarget,
                    destinationTarget = mc.mainRenderTarget,
                    maskTarget = outlineTarget,
                    backgroundTarget = preHandTarget,
                    imageTexture = imageLocation
                )
                debugLog(
                    "composite ok: frame=$frameIndex dual=${HandShaderConfig.dualShaderMode()} modes=${HandShaderConfig.activeModes().joinToString { HandShaderConfig.shaderName(it) }} image=${imageLocation != null} outline=${HandShaderConfig.renderOutline()}"
                )
            }
            clearRenderTarget(outlineTarget)
        } else {
            debugLog("composite skipped: outlineTarget=false")
        }

        debugLog(
            "apply ok: frame=$frameIndex overrides=$overrideCount items=$itemOverrideCount models=$modelOverrideCount modelParts=$modelPartOverrideCount"
        )
        handOutlineActive = false
        handCompositePending = false
        outlineBatchFlushed = false
    }

    @JvmStatic
    fun renderCustomGlassSubmitsIfNeeded() {
        // No-op: custom replay path removed to keep transforms in vanilla hand space.
    }

    @JvmStatic
    fun flushOutlineBatchIfNeeded() {
        if (!handCompositePending || outlineBatchFlushed) return
        val mc = Minecraft.getInstance()
        mc.renderBuffers().outlineBufferSource().endOutlineBatch()
        outlineBatchFlushed = true
    }

    private fun capturePreHandTarget(mainTarget: RenderTarget) {
        if (!HandShaderConfig.requiresBackgroundCapture()) return

        val width = mainTarget.width
        val height = mainTarget.height
        if (preHandTarget == null) {
            preHandTarget = TextureTarget("${HandShadersClient.MOD_ID}_prehand", width, height, false)
        } else if (preHandTarget?.width != width || preHandTarget?.height != height) {
            preHandTarget?.resize(width, height)
        }

        val target = preHandTarget ?: return
        copyColor(mainTarget, target)
    }

    private fun captureCleanHandTarget(mainTarget: RenderTarget) {
        val width = mainTarget.width
        val height = mainTarget.height
        if (cleanHandTarget == null) {
            cleanHandTarget = TextureTarget("${HandShadersClient.MOD_ID}_cleanhand", width, height, false)
        } else if (cleanHandTarget?.width != width || cleanHandTarget?.height != height) {
            cleanHandTarget?.resize(width, height)
        }

        val target = cleanHandTarget ?: return
        copyColor(mainTarget, target)
    }

    private fun copyColor(source: RenderTarget, destination: RenderTarget) {
        val sourceTexture = source.colorTexture ?: return
        val destinationTexture = destination.colorTexture ?: return
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.copyTextureToTexture(
            sourceTexture,
            destinationTexture,
            0,
            0,
            0,
            0,
            0,
            minOf(source.width, destination.width),
            minOf(source.height, destination.height)
        )
    }

    private fun clearRenderTarget(target: RenderTarget) {
        val colorTexture = target.colorTexture ?: return
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        if (target.useDepth) {
            val depthTexture = target.depthTexture ?: return
            encoder.clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0)
        } else {
            encoder.clearColorTexture(colorTexture, 0)
        }
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
        // Force a non-zero outline submit color so the outline pass runs, but keep it almost invisible.
        // Visible outline tint is applied later in the composite shader via OutlineColor uniform.
        return 0x01000000
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
