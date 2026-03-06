package me.cheater.handshaders.mixin.rendering;

import me.cheater.handshaders.features.HandShaderRenderer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionHandOutlineMixin {
    @ModifyVariable(
        method = "submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 2
    )
    private int handshaders$overrideItemOutlineColor(int outlineColor) {
        HandShaderRenderer.INSTANCE.markItemOverride();
        return HandShaderRenderer.INSTANCE.overrideOutlineColor(outlineColor);
    }

    @ModifyVariable(
        method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 3
    )
    private int handshaders$overrideModelOutlineColor(int outlineColor) {
        HandShaderRenderer.INSTANCE.markModelOverride();
        return HandShaderRenderer.INSTANCE.overrideOutlineColor(outlineColor);
    }

    @ModifyVariable(
        method = "submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 3
    )
    private int handshaders$overrideModelPartOutlineColor(int outlineColor) {
        HandShaderRenderer.INSTANCE.markModelPartOverride();
        return HandShaderRenderer.INSTANCE.overrideOutlineColor(outlineColor);
    }
}
