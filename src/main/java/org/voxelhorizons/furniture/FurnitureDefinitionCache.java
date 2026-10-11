package org.voxelhorizons.furniture;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.furniture.model.FurnitureDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * A single parser cache for placed definitions and render-only variants.
 *
 * An animated DISPLAY can ask for the same two definitions 20 times per
 * second. Immutable VoxelCore content is safe to reuse until its revision
 * changes. This cache is scoped to the furniture manager (not global),
 * keeps negative lookups, and never caches parse exceptions.
 *
 * Only access from the Paper server thread, like FurnitureManager.
 */
final class FurnitureDefinitionCache {
    private final Function<ContentID, Optional<ItemDefinition>> lookup;
    private final Function<ItemDefinition, Optional<FurnitureDefinition>> parser;
    private final LongSupplier revision;
    private final Map<ContentID, Entry> resolved = new LinkedHashMap<ContentID, Entry>();
    private long cachedRevision = Long.MIN_VALUE;

    private static final class Entry {
        final boolean abstractItem;
        final Optional<FurnitureDefinition> definition;

        Entry(boolean abstractItem, Optional<FurnitureDefinition> definition) {
            this.abstractItem = abstractItem;
            this.definition = definition;
        }
    }

    FurnitureDefinitionCache(Function<ContentID, Optional<ItemDefinition>> lookup,
            Function<ItemDefinition, Optional<FurnitureDefinition>> parser,
            LongSupplier revision) {
        this.lookup = lookup;
        this.parser = parser;
        this.revision = revision;
    }

    /** Regular, placeable furniture never includes abstract VoxelCore items. */
    Optional<FurnitureDefinition> definition(ContentID id) {
        Entry entry = resolve(id);
        return entry.abstractItem ? Optional.<FurnitureDefinition>empty() : entry.definition;
    }

    /**
     * Render-only children can be abstract (authored blockstate or
     * animation.use variants). Missing/unrelated items use the fallback.
     */
    FurnitureDefinition renderDefinition(ContentID id, FurnitureDefinition fallback) {
        return resolve(id).definition.orElse(fallback);
    }

    private Entry resolve(ContentID id) {
        long currentRevision = revision.getAsLong();
        if (currentRevision != cachedRevision) {
            resolved.clear();
            cachedRevision = currentRevision;
        }

        Entry cached = resolved.get(id);
        if (cached != null) return cached;

        Optional<ItemDefinition> item = lookup.apply(id);
        Entry parsed = item.isPresent()
                ? new Entry(item.get().abstractDefinition(), parser.apply(item.get()))
                : new Entry(false, Optional.<FurnitureDefinition>empty());
        resolved.put(id, parsed);
        return parsed;
    }
}
