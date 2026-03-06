package me.cheater.handshaders.mixin.rendering;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.cheater.handshaders.features.HandShaderRenderer;
import me.cheater.handshaders.mixin.accessor.LevelRendererAccessor;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererHandOutlineMixin implements LevelRendererAccessor {
    @Inject(method = "entityOutlineTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;", at = @At("HEAD"), cancellable = true)
    private void handshaders$useBackingOutlineTargetForHandPass(CallbackInfoReturnable<RenderTarget> cir) {
        if (!HandShaderRenderer.INSTANCE.isHandOutlineRoutingActive()) return;
        cir.setReturnValue(this.getBackingEntityOutlineTarget());
    }
}
