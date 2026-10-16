package com.dinodread.client;

import com.dinodread.DinoDread;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

// Client-only entry point. Never reference this package from common code: a dedicated server will crash.
@Mod(value = DinoDread.MODID, dist = Dist.CLIENT)
public class DinoDreadClient {
    public DinoDreadClient() {
        // Renderers, fog hooks and client-only sound code are registered from here as milestones land.
    }
}
