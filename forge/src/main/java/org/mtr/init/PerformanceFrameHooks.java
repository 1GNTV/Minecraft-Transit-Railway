package org.mtr.init;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mtr.mod.Init;
import org.mtr.mod.render.InstancedModelRenderer;
import org.mtr.mod.render.RenderVehicles;

/** One epoch surrounds main and Oculus shadow passes, including shadow-first ordering. */
@Mod.EventBusSubscriber(modid = Init.MOD_ID, value = Dist.CLIENT)
public final class PerformanceFrameHooks {
    private static Object world;
    private PerformanceFrameHooks() {}
    @SubscribeEvent public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Object current = Minecraft.getInstance().level;
            if (world != current) { InstancedModelRenderer.clear(); org.mtr.mod.render.ModelDetailCache.clear(); org.mtr.mod.render.RailSectorBatcher.clear(); org.mtr.mod.render.PredictiveVehiclePreloader.clear(); org.mtr.mod.resource.ModelPreparation.clear(); world = current; }
            org.mtr.mod.render.StaticRegionRenderer.beginFrame();
            org.mtr.mod.render.ClassicRailGpuCache.beginFrame();
            org.mtr.mod.render.RailLightFrameCache.beginFrame();
            RenderVehicles.beginFrame();
        } else { RenderVehicles.endFrame(); org.mtr.mod.render.PredictiveVehiclePreloader.drain(); }
    }
}
