package org.sillon.qa;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

@Mod("signageqa")
public final class SignageQaMod {
    public SignageQaMod(){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->SignageVisualQa::register);}
}
