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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.mtr.mod.render.ModelDetailCache;
import org.mtr.mod.render.ModelPartVisibility;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "org.mtr.mapping.render.batch.BatchManager$RenderCall", remap = false)
public abstract class RenderCallMetricsMixin implements RenderCallData {
    @Shadow @Final public VertexArray vertexArray;
    @Shadow @Final public VertexAttributeState vertexAttributeState;
    @Unique private RenderPipelineMetrics.Family mtr$family;
    @Unique private double mtr$distance;
    @Override public VertexArray mtr$array() { return ModelDetailCache.select(vertexArray,mtr$family,mtr$distance); }
    @Override public VertexAttributeState mtr$attributes() { return vertexAttributeState; }
    @Override public RenderPipelineMetrics.Family mtr$family() { return mtr$family; }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void mtr$tag(CallbackInfo ci) {
        mtr$family = RenderPipelineMetrics.captureFamily();
        mtr$distance = ModelDetailCache.captureDistance();
        RenderPipelineMetrics.queued(mtr$family);
    }
    @Redirect(method="draw",at=@At(value="FIELD",target="Lorg/mtr/mapping/render/batch/BatchManager$RenderCall;vertexArray:Lorg/mtr/mapping/render/object/VertexArray;"))
    private VertexArray mtr$detailArray(@org.spongepowered.asm.mixin.injection.Coerce Object ignored){return mtr$array();}
    @Inject(method = "draw", at = @At("HEAD"),cancellable=true)
    private void mtr$drawing(CallbackInfo ci) { if (!ModelPartVisibility.visible(vertexArray,vertexAttributeState)) { ci.cancel(); return; } RenderPipelineMetrics.drawing(mtr$family); }
}

