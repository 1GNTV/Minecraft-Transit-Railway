package org.sillon.qa;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

@Mod("signageqa")
public final class SignageQaMod {
    public SignageQaMod(){SignageWorldQa.register();DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->{SignageVisualQa.register();StationDecorVisualQa.register();});}
}
