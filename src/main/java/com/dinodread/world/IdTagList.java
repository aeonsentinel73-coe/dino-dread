package com.dinodread.world;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** STUB (replaced by tasks/017): a config list of registry ids and #tags. */
public final class IdTagList<T> {
    public IdTagList(String configName, ResourceKey<? extends Registry<T>> registry) {}

    public boolean matches(List<? extends String> configured, Holder<T> holder) {
        return false;
    }
}
