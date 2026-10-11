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
        final ItemDefinition item;
        // null means the parser was not invoked yet. Abstract variants are
        // parsed only on render lookups, never in placeable-item enumeration.
        Optional<FurnitureDefinition> parsed;

        Entry(ItemDefinition item) {
            this.item = item;
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
        return entry.item == null || entry.item.abstractDefinition()
                ? Optional.<FurnitureDefinition>empty() : parse(entry);
    }

    /**
     * Render-only children can be abstract (authored blockstate or
     * animation.use variants). Missing/unrelated items use the fallback.
     */
    FurnitureDefinition renderDefinition(ContentID id, FurnitureDefinition fallback) {
        return parse(resolve(id)).orElse(fallback);
    }

    private Optional<FurnitureDefinition> parse(Entry entry) {
        if (entry.item == null) return Optional.empty();
        if (entry.parsed == null) {
            // Exceptions are not cached: a bad parse is still reported, and
            // the next load/revision attempt can retry validation normally.
            entry.parsed = parser.apply(entry.item);
        }
        return entry.parsed;
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
        Entry entry = new Entry(item.orElse(null));
        resolved.put(id, entry);
        return entry;
    }
}
