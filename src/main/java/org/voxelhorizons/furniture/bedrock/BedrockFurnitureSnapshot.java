package org.voxelhorizons.furniture.bedrock;

import org.bukkit.Location;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureDisplayPartDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;

import java.util.*;

/**
 * Immutable Paper-authoritative view of placed furniture for future Geyser bridges.
 *
 * This is NOT a secondary entity/gameplay system: no spawn, mutate, interaction,
 * ownership or inventory methods are exposed. Capture synchronously on Paper's
 * main thread, then serialize/copy the snapshot off-thread if necessary.
 */
public final class BedrockFurnitureSnapshot {
    public static final int SCHEMA = 1;
    private final long contentRevision;
    private final List<Map<String,Object>> entries;

    private BedrockFurnitureSnapshot(long contentRevision, List<Map<String,Object>> entries) {
        this.contentRevision = contentRevision;
        this.entries = Collections.unmodifiableList(entries);
    }

    public long contentRevision() { return contentRevision; }
    public List<Map<String,Object>> entries() { return entries; }

    public Map<String,Object> asMap() {
        Map<String,Object> envelope = new LinkedHashMap<String,Object>();
        envelope.put("schema", Integer.valueOf(SCHEMA));
        envelope.put("content_revision", Long.valueOf(contentRevision));
        envelope.put("furniture", entries);
        return Collections.unmodifiableMap(envelope);
    }

    public static BedrockFurnitureSnapshot capture(long revision,
                                                    Collection<FurnitureInstance> instances,
                                                    Map<ContentID,FurnitureDefinition> definitions) {
        if (instances == null || definitions == null)
            throw new IllegalArgumentException("Furniture snapshot inputs must not be null");
        List<FurnitureInstance> sorted = new ArrayList<FurnitureInstance>(instances);
        Collections.sort(sorted, (a,b) -> a.id().toString().compareTo(b.id().toString()));
        List<Map<String,Object>> data = new ArrayList<Map<String,Object>>();
        for (FurnitureInstance instance : sorted) {
            FurnitureDefinition definition = definitions.get(instance.definitionId());
            if (definition == null)
                throw new IllegalArgumentException("Missing furniture definition " + instance.definitionId());
            data.add(captureOne(instance, definition));
        }
        return new BedrockFurnitureSnapshot(revision, data);
    }

    private static Map<String,Object> captureOne(FurnitureInstance instance, FurnitureDefinition definition) {
        Location location = instance.location();
        if (location.getWorld() == null)
            throw new IllegalArgumentException("Furniture snapshot instance has no world: " + instance.id());
        Map<String,Object> value = new LinkedHashMap<String,Object>();
        value.put("instance_uuid", instance.id().toString());
        value.put("world_uuid", location.getWorld().getUID().toString());
        value.put("world_name", location.getWorld().getName());
        value.put("definition", instance.definitionId().toString());
        value.put("model_item", modelId(instance,definition));
        value.put("renderer", instance.renderer().name());
        value.put("position", xyz(location.getX(),location.getY(),location.getZ()));
        value.put("yaw", Float.valueOf(instance.yaw()));
        value.put("render_yaw", Float.valueOf(Float.isNaN(instance.renderedYaw())
                ? instance.yaw() : instance.renderedYaw()));
        value.put("render_signature", instance.renderSignature() == null ? "" : instance.renderSignature());
        value.put("scale", xyz(definition.scaleX(),definition.scaleY(),definition.scaleZ()));
        value.put("local_offset", xyz(definition.offsetX(),definition.offsetY(),definition.offsetZ()));
        value.put("offset_rotation", Float.valueOf(definition.offsetRotation()));
        value.put("view_distance", Float.valueOf(definition.viewDistance()));
        value.put("hitbox", xyz(definition.width(), definition.height(), 0));
        value.put("hitbox_offset", xyz(definition.hitboxOffsetX(),definition.hitboxOffsetY(),definition.hitboxOffsetZ()));
        value.put("dye_rgb", instance.dyeColor());
        value.put("use_animation_active", Boolean.valueOf(instance.useAnimationActive()));

        List<UUID> entities = instance.entities();
        Map<String,Object> links = new LinkedHashMap<String,Object>();
        links.put("display_entity_uuid", entities.size() > 0 ? entities.get(0).toString() : null);
        links.put("interaction_entity_uuid", entities.size() > 1 ? entities.get(1).toString() : null);
        List<Map<String,Object>> parts = new ArrayList<Map<String,Object>>();
        int index=2;
        for (FurnitureDisplayPartDefinition part : definition.displayParts()) {
            Map<String,Object> item = new LinkedHashMap<String,Object>();
            item.put("id",part.id());
            item.put("type",part.type().name());
            item.put("java_entity_uuid", entities.size()>index ? entities.get(index).toString() : null);
            item.put("model_item",part.modelItem()==null ? null : part.modelItem().toString());
            item.put("text",part.text());
            item.put("billboard",part.billboard());
            item.put("local_offset",xyz(part.offsetX(),part.offsetY(),part.offsetZ()));
            item.put("scale",Float.valueOf(part.scale()));
            item.put("bob",Boolean.valueOf(part.bob()));
            item.put("spin",Boolean.valueOf(part.spin()));
            parts.add(Collections.unmodifiableMap(item));
            index++;
        }
        links.put("parts",Collections.unmodifiableList(parts));
        value.put("entity_links",Collections.unmodifiableMap(links));
        return Collections.unmodifiableMap(value);
    }

    private static String modelId(FurnitureInstance instance, FurnitureDefinition definition) {
        ContentID id = instance.renderedModel();
        return (id == null ? definition.modelItemId() : id).toString();
    }

    private static List<Double> xyz(double x,double y,double z) {
        return Collections.unmodifiableList(Arrays.asList(Double.valueOf(x),Double.valueOf(y),Double.valueOf(z)));
    }
}
