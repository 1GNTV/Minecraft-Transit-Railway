package org.mtr.init.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import org.mtr.init.ForgeWorldTextBatch;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.OrderedText;
import org.mtr.mod.render.WorldTextBatch;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.mtr.mapping.mapper.GraphicsHolder", remap = false)
public abstract class GraphicsHolderTextMixin {
    @Shadow @Final private PoseStack matrixStack;
    @Shadow @Final private GuiGraphics drawContext;
    @Unique private boolean mtr$batchText() { return matrixStack != null && drawContext == null && WorldTextBatch.active(); }
    @Inject(method = "drawText(Lorg/mtr/mapping/holder/MutableText;IIIZI)V", at = @At("HEAD"), cancellable = true)
    private void mtr$mutable(MutableText text, int x, int y, int color, boolean shadow, int light, CallbackInfo ci) {
        if (mtr$batchText()) { ForgeWorldTextBatch.draw(text.data, matrixStack, x, y, color, shadow, light); ci.cancel(); }
    }
    @Inject(method = "drawText(Lorg/mtr/mapping/holder/OrderedText;IIIZI)V", at = @At("HEAD"), cancellable = true)
    private void mtr$ordered(OrderedText text, int x, int y, int color, boolean shadow, int light, CallbackInfo ci) {
        if (mtr$batchText()) { ForgeWorldTextBatch.draw(text.data, matrixStack, x, y, color, shadow, light); ci.cancel(); }
    }
    @Inject(method = "drawText(Ljava/lang/String;IIIZI)V", at = @At("HEAD"), cancellable = true)
    private void mtr$string(String text, int x, int y, int color, boolean shadow, int light, CallbackInfo ci) {
        if (mtr$batchText()) { ForgeWorldTextBatch.draw(text, matrixStack, x, y, color, shadow, light); ci.cancel(); }
    }
    @Inject(method = {"createVertexConsumer", "drawTextureInWorld", "drawLineInWorld", "renderEntity"}, at = @At("HEAD"))
    private void mtr$boundary(CallbackInfo ci) { WorldTextBatch.boundary(); }
}
