package me.cheater.handshaders.mixin.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cheater.handshaders.features.HandShaderRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererOutlineMixin {
    @Inject(
        method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
        at = @At("HEAD")
    )
    private void handshaders$captureHandPass(float partialTick, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LocalPlayer localPlayer, int light, CallbackInfo ci) {
        HandShaderRenderer.INSTANCE.captureBeforeHandRender();
        HandShaderRenderer.INSTANCE.suppressMainColorWritesIfNeeded();
    }

    @Inject(
        method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
        at = @At("TAIL")
    )
    private void handshaders$outlineHandPass(float partialTick, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LocalPlayer localPlayer, int light, CallbackInfo ci) {
        HandShaderRenderer.INSTANCE.finishHandSubmission();
    }
}
