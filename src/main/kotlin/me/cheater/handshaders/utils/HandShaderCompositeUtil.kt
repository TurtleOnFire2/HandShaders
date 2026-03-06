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
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.ResourceLocation
import java.awt.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.OptionalInt

object HandShaderCompositeUtil {
    private val pipeline: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation("${HandShadersClient.MOD_ID}/pipeline/hand_outline_composite")
            .withVertexShader("core/screenquad")
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath(HandShadersClient.MOD_ID, "post/hand_outline_composite"))
            .withSampler("MainSampler")
            .withSampler("MaskSampler")
            .withSampler("ImageSampler")
            .withUniform("CompositeConfig", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build()
    )

    private var scratchTarget: TextureTarget? = null
    private var compositeUniformBuffer: GpuBuffer? = null

    fun composite(
        mainTarget: RenderTarget,
        maskTarget: RenderTarget,
        color: Color,
        fillAlpha: Int,
        imageAlpha: Int,
        imageTexture: ResourceLocation?
    ) {
        val width = mainTarget.width
        val height = mainTarget.height
        ensureScratchTarget(width, height)

        val scratch = scratchTarget ?: return
        val mainView = mainTarget.colorTextureView ?: return
        val maskView = maskTarget.colorTextureView ?: return
        val imageView = imageTexture?.let { Minecraft.getInstance().textureManager.getTexture(it).textureView }
        val hasImage = imageTexture != null && imageView != null && imageAlpha > 0

        writeUniforms(color, fillAlpha, imageAlpha, hasImage)
        val uniformBuffer = compositeUniformBuffer ?: return

        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.createRenderPass(
            { "HandShaders composite" },
            scratch.colorTextureView,
            OptionalInt.empty()
        ).use { renderPass ->
            renderPass.setPipeline(pipeline)
            RenderSystem.bindDefaultUniforms(renderPass)
            renderPass.setUniform("CompositeConfig", uniformBuffer)
            renderPass.bindSampler("MainSampler", mainView)
            renderPass.bindSampler("MaskSampler", maskView)
            renderPass.bindSampler("ImageSampler", imageView ?: mainView)
            renderPass.draw(0, 3)
        }

        scratch.blitAndBlendToTexture(mainView)
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

    private fun writeUniforms(color: Color, fillAlpha: Int, imageAlpha: Int, hasImage: Boolean) {
        if (compositeUniformBuffer == null) {
            compositeUniformBuffer = RenderSystem.getDevice().createBuffer(
                { "${HandShadersClient.MOD_ID} hand composite uniform" },
                GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_MAP_WRITE,
                32
            )
        }

        val uniformBuffer = compositeUniformBuffer ?: return
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.mapBuffer(uniformBuffer.slice(), false, true).use { mapped ->
            val data: ByteBuffer = mapped.data().order(ByteOrder.nativeOrder())
            data.putFloat(0, color.red / 255.0f)
            data.putFloat(4, color.green / 255.0f)
            data.putFloat(8, color.blue / 255.0f)
            data.putFloat(12, fillAlpha.coerceIn(0, 255) / 255.0f)
            data.putFloat(16, imageAlpha.coerceIn(0, 255) / 255.0f)
            data.putFloat(20, if (hasImage) 1.0f else 0.0f)
            data.putFloat(24, 0.0f)
            data.putFloat(28, 0.0f)
        }
    }
}
