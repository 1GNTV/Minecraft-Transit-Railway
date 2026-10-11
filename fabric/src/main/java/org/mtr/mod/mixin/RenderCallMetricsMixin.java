package org.mtr.mod.mixin;
import org.mtr.mod.render.RenderPipelineMetrics;
import org.mtr.mod.render.RenderCallData;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "org.mtr.mapping.render.batch.BatchManager$RenderCall", remap = false)
public abstract class RenderCallMetricsMixin implements RenderCallData {
    @Shadow @Final public VertexArray vertexArray;
    @Shadow @Final public VertexAttributeState vertexAttributeState;
    @Unique private RenderPipelineMetrics.Family mtr$family;
    @Override public VertexArray mtr$array() { return vertexArray; }
    @Override public VertexAttributeState mtr$attributes() { return vertexAttributeState; }
    @Override public RenderPipelineMetrics.Family mtr$family() { return mtr$family; }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void mtr$tag(CallbackInfo ci) {
        mtr$family = RenderPipelineMetrics.captureFamily();
        RenderPipelineMetrics.queued(mtr$family);
    }
    @Inject(method = "draw", at = @At("HEAD"))
    private void mtr$drawing(CallbackInfo ci) { RenderPipelineMetrics.drawing(mtr$family); }
}

