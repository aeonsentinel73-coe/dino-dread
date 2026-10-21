package com.dinodread.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dinodread.DinoDread;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * A config list of registry ids and #tags (for example {@code minecraft:zombie} or {@code #minecraft:skeletons}),
 * parsed once and re-parsed only when the config value changes. Safe to call from any thread (world generation
 * calls it): the parsed lists are immutable snapshots.
 */
public final class IdTagList<T> {
    private final String configName;
    private final ResourceKey<? extends Registry<T>> registry;
    private volatile Parsed<T> parsed;

    private record Parsed<T>(List<? extends String> source, Set<Identifier> ids, List<TagKey<T>> tags) {}

    public IdTagList(String configName, ResourceKey<? extends Registry<T>> registry) {
        this.configName = configName;
        this.registry = registry;
    }

    public boolean matches(List<? extends String> configured, Holder<T> holder) {
        Parsed<T> current = parsed;
        if (current == null || !configured.equals(current.source())) {
            current = parse(configured);
            parsed = current;
        }

        for (Identifier id : current.ids()) {
            if (holder.is(id)) {
                return true;
            }
        }

        for (TagKey<T> tag : current.tags()) {
            if (holder.is(tag)) {
                return true;
            }
        }

        return false;
    }

    private Parsed<T> parse(List<? extends String> configured) {
        Set<Identifier> ids = new HashSet<>();
        List<TagKey<T>> tags = new ArrayList<>();
        for (String entry : configured) {
            if (entry.startsWith("#")) {
                Identifier id = Identifier.tryParse(entry.substring(1));
                if (id == null) {
                    DinoDread.LOGGER.warn("Skipping malformed tag entry in {} config: {}", configName, entry);
                    continue;
                }

                tags.add(TagKey.create(registry, id));
            } else {
                Identifier id = Identifier.tryParse(entry);
                if (id == null) {
                    DinoDread.LOGGER.warn("Skipping malformed id entry in {} config: {}", configName, entry);
                    continue;
                }

                ids.add(id);
            }
        }

        return new Parsed<>(configured, ids, tags);
    }
}
