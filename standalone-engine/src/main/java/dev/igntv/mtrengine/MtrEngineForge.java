package dev.igntv.mtrengine;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Independent mod identity: install next to official MTR.
 * Do NOT ship the whole MTR jar or original fork in this addon.
 */
@Mod(MtrEngineForge.MOD_ID)
public final class MtrEngineForge {
    public static final String MOD_ID = "mtrengine";
    public static final Logger LOGGER = LogManager.getLogger("MTR Engine");
    public MtrEngineForge() {
        LOGGER.info("MTR Engine standalone render hooks loaded (prototype; original MTR drawing preserved)");
    }
}
