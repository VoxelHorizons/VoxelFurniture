package org.voxelhorizons.furniture.bedrock;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furniture.model.FurnitureRendererType;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public final class BedrockFurnitureSnapshotTest {
    private static World mockWorld(UUID uuid) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},
                (proxy,method,args) -> {
                    if ("getUID".equals(method.getName())) return uuid;
                    if ("getName".equals(method.getName())) return "realms";
                    if ("toString".equals(method.getName())) return "realms";
                    if ("hashCode".equals(method.getName())) return uuid.hashCode();
                    if ("equals".equals(method.getName())) return proxy==args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test public void serializesVersionedImmutableStateWithoutInventoryOrOwnershipCopies() {
        ContentID id = ContentID.of("voxel","oak_chair");
        ContentID model = ContentID.of("voxel","oak_chair_model");
        FurnitureDefinition definition = new FurnitureDefinition(
                id,model,id,FurnitureRendererType.DISPLAY,1.0f,1.5f,1.25f,45f,
                0.5,0.1,-0.25,Collections.emptyList());
        Map<ContentID,FurnitureDefinition> definitions = new HashMap<ContentID,FurnitureDefinition>();
        definitions.put(id,definition);
        UUID world = UUID.fromString("00000000-0000-0000-0000-000000000005");
        UUID first=UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID second=UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID visual=UUID.fromString("00000000-0000-0000-0000-000000000007");
        UUID hitbox=UUID.fromString("00000000-0000-0000-0000-000000000008");
        Location loc = new Location(mockWorld(world),10,64,20);
        FurnitureInstance a = new FurnitureInstance(second,id,loc,45f,FurnitureRendererType.DISPLAY,
                Arrays.asList(visual,hitbox),Collections.emptyList(),model,45f,"render-v1",
                Collections.emptyList(),0xff00ff,true);
        FurnitureInstance b = new FurnitureInstance(first,id,loc,90f,FurnitureRendererType.DISPLAY,
                Collections.emptyList(),Collections.emptyList());
        BedrockFurnitureSnapshot captured = BedrockFurnitureSnapshot.capture(42L,Arrays.asList(a,b),definitions);
        Map<String,Object> map=captured.asMap();
        assertEquals(1,map.get("schema"));
        assertEquals(42L,map.get("content_revision"));
        List<?> rows=(List<?>) map.get("furniture");
        assertEquals(2,rows.size());
        Map<?,?> firstRow=(Map<?,?>) rows.get(0);
        assertEquals(first.toString(),firstRow.get("instance_uuid"));
        Map<?,?> last=(Map<?,?>) rows.get(1);
        assertEquals("voxel:oak_chair_model",last.get("model_item"));
        assertEquals(world.toString(),last.get("world_uuid"));
        assertEquals(true,last.get("use_animation_active"));
        assertEquals(0xff00ff,last.get("dye_rgb"));
        assertEquals("render-v1",last.get("render_signature"));
        assertEquals(visual.toString(),((Map<?,?>)last.get("entity_links")).get("display_entity_uuid"));
        assertEquals(hitbox.toString(),((Map<?,?>)last.get("entity_links")).get("interaction_entity_uuid"));
        assertFalse(last.containsKey("inventory_contents"));
        assertFalse(last.containsKey("owner"));
        try { rows.clear(); fail("Snapshot must be immutable"); }
        catch(UnsupportedOperationException expected) {}
    }

    @Test public void refusesUnknownDefinitionsInsteadOfDroppingFurniture() {
        ContentID id=ContentID.of("voxel","missing");
        FurnitureInstance a = new FurnitureInstance(UUID.randomUUID(),id,
                new Location(mockWorld(UUID.randomUUID()),0,0,0),0f,FurnitureRendererType.DISPLAY,
                Collections.emptyList(),Collections.emptyList());
        try {
            BedrockFurnitureSnapshot.capture(1L,Collections.singletonList(a),Collections.emptyMap());
            fail("Should report unresolved furniture definition");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing furniture definition"));
        }
    }
}
