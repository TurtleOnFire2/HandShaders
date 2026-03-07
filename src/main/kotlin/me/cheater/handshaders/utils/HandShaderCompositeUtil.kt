package me.cheater.handshaders.utils

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import me.cheater.handshaders.HandShadersClient
import me.cheater.handshaders.config.CHandShaders
import me.cheater.handshaders.config.HandShaderConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.ResourceLocation
import java.awt.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalInt

object HandShaderCompositeUtil {
    private const val UNIFORM_VEC4_COUNT = 16
    private const val UNIFORM_BUFFER_SIZE = UNIFORM_VEC4_COUNT * 16

    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation("${HandShadersClient.MOD_ID}/pipeline/hand_outline_composite")
            .withVertexShader("core/screenquad")
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath(HandShadersClient.MOD_ID, "post/hand_outline_composite"))
            .withSampler("MainSampler")
            .withSampler("MaskSampler")
            .withSampler("BackgroundSampler")
            .withSampler("ImageSampler")
            .withUniform("CompositeConfig", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build()
    )

    private var scratchTarget: TextureTarget? = null
    private var compositeUniformBuffer: GpuBuffer? = null

    fun composite(
        sourceTarget: RenderTarget,
        destinationTarget: RenderTarget,
        maskTarget: RenderTarget,
        backgroundTarget: RenderTarget?,
        imageTexture: ResourceLocation?
    ) {
        val width = destinationTarget.width
        val height = destinationTarget.height
        ensureScratchTarget(width, height)

        val scratch = scratchTarget ?: return
        val scratchView = scratch.colorTextureView ?: return
        val sourceView = sourceTarget.colorTextureView ?: return
        val destinationView = destinationTarget.colorTextureView ?: return
        val maskView = maskTarget.colorTextureView ?: return
        val backgroundView = backgroundTarget?.colorTextureView ?: sourceView
        val imageView = imageTexture?.let { Minecraft.getInstance().textureManager.getTexture(it).textureView }
        val hasImage = imageTexture != null && imageView != null && HandShaderConfig.hasImageOverlay()

        writeUniforms(width, height, hasImage)
        val uniformBuffer = compositeUniformBuffer ?: return

        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.createRenderPass(
            { "HandShaders composite" },
            scratchView,
            OptionalInt.empty()
        ).use { renderPass ->
            renderPass.setPipeline(pipeline)
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform("CompositeConfig", uniformBuffer)
            renderPass.bindSampler("MainSampler", sourceView)
            renderPass.bindSampler("MaskSampler", maskView)
            renderPass.bindSampler("BackgroundSampler", backgroundView)
            renderPass.bindSampler("ImageSampler", imageView ?: sourceView)
            renderPass.draw(0, 3)
        }

        copyColor(scratch, destinationTarget)
    }

    private fun ensureScratchTarget(width: Int, height: Int) {
        if (scratchTarget == null) {
            scratchTarget = TextureTarget("${HandShadersClient.MOD_ID}_hand_composite", width, height, false)
            return
        }

        if (scratchTarget?.width != width || scratchTarget?.height != height) {
            scratchTarget?.resize(width, height)
        }
    }

    private fun writeUniforms(width: Int, height: Int, hasImage: Boolean) {
        if (compositeUniformBuffer == null) {
            compositeUniformBuffer = RenderSystem.getDevice().createBuffer(
                { "${HandShadersClient.MOD_ID} hand composite uniform" },
                GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_MAP_WRITE,
                UNIFORM_BUFFER_SIZE
            )
        }

        val uniformBuffer = compositeUniformBuffer ?: return
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.mapBuffer(uniformBuffer.slice(), false, true).use { mapped ->
            val data: ByteBuffer = mapped.data().order(ByteOrder.nativeOrder())

            fun putVec4(index: Int, x: Float, y: Float, z: Float, w: Float) {
                val offset = index * 16
                data.putFloat(offset, x)
                data.putFloat(offset + 4, y)
                data.putFloat(offset + 8, z)
                data.putFloat(offset + 12, w)
            }

            fun putColor(index: Int, color: Color) {
                putVec4(index, color.red / 255.0f, color.green / 255.0f, color.blue / 255.0f, color.alpha / 255.0f)
            }

            putVec4(0, System.nanoTime() / 1_000_000_000.0f, width.toFloat(), height.toFloat(), 0.0f)
            putVec4(
                1,
                HandShaderConfig.mainShaderMode().toFloat(),
                if (HandShaderConfig.dualShaderMode()) 1.0f else 0.0f,
                HandShaderConfig.shader1Mode().toFloat(),
                HandShaderConfig.shader2Mode().toFloat()
            )
            putVec4(
                2,
                HandShaderConfig.blendMode().toFloat(),
                HandShaderConfig.blendIntensity(),
                HandShaderConfig.shader1Strength(),
                HandShaderConfig.shader2Strength()
            )
            putVec4(
                3,
                HandShaderConfig.imageAlpha / 255.0f,
                if (hasImage) 1.0f else 0.0f,
                HandShaderConfig.mainShaderStrength(),
                0.0f
            )
            val outlineColor = if (HandShaderConfig.renderOutline()) HandShaderConfig.outlineColor() else Color(0, 0, 0, 0)
            putColor(4, outlineColor)
            putColor(5, CHandShaders.solidColor1)
            putColor(6, CHandShaders.solidColor2)
            putVec4(7, CHandShaders.gradientSpeed, 0.0f, 0.0f, 0.0f)
            putVec4(8, CHandShaders.chromaSpeed, CHandShaders.chromaSaturation, CHandShaders.chromaBrightness, 0.0f)
            putColor(9, CHandShaders.smokeColor)
            putVec4(10, CHandShaders.smokeIntensity, CHandShaders.smokeSpeed, 0.0f, 0.0f)
            putColor(11, CHandShaders.glowColor)
            putVec4(12, if (CHandShaders.fillGlow) 1.0f else 0.0f, CHandShaders.glowRadius, CHandShaders.glowPower, CHandShaders.glowDispersion)
            putVec4(13, CHandShaders.glassBlurSize, CHandShaders.glassQuality, CHandShaders.glassDirection, CHandShaders.glassRefraction)
            putVec4(14, CHandShaders.glassBrightness, if (CHandShaders.glassChromatic) 1.0f else 0.0f, if (CHandShaders.glassDistortion) 1.0f else 0.0f, if (CHandShaders.glassHideHand) 1.0f else 0.0f)
            putVec4(15, CHandShaders.glassBackgroundBlur, 0.0f, 0.0f, 0.0f)
        }
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
}
