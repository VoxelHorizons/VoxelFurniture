package org.voxelhorizons.furniture.bedrock;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public final class ProjectionModelChangesTest {
    private static Map<String,Object> feed(UUID display,String model,UUID part,String partModel) {
        Map<String,Object> partRow=new HashMap<String,Object>();
        partRow.put("type","ITEM");
        partRow.put("java_entity_uuid",part.toString());
        partRow.put("model_item",partModel);
        Map<String,Object> text=new HashMap<String,Object>();
        text.put("type","TEXT");
        text.put("java_entity_uuid",UUID.randomUUID().toString());
        text.put("model_item",null);
        Map<String,Object> links=new HashMap<String,Object>();
        links.put("display_entity_uuid",display.toString());
        links.put("interaction_entity_uuid",UUID.randomUUID().toString());
        links.put("parts",Arrays.asList(partRow,text));
        Map<String,Object> row=new HashMap<String,Object>();
        row.put("model_item",model);
        row.put("entity_links",links);
        Map<String,Object> root=new HashMap<String,Object>();
        root.put("schema",1);
        root.put("furniture",Collections.singletonList(row));
        return root;
    }

    @Test public void indexesOnlyProjectedDisplayAndItemPartIdentities() {
        UUID display=UUID.randomUUID(),part=UUID.randomUUID();
        Map<UUID,String> models=ProjectionModelChanges.visualModels(feed(display,"voxel:bench",part,"voxel:cushion"),"furniture");
        assertEquals(2,models.size());
        assertEquals("voxel:bench",models.get(display));
        assertEquals("voxel:cushion",models.get(part));
        assertTrue(ProjectionModelChanges.visualModels(feed(display,"a",part,"b"),"vehicles").isEmpty());
    }

    @Test public void reportsModelSwapsButNotSpawnsOrRemovals() {
        UUID display=UUID.randomUUID(),part=UUID.randomUUID();
        Map<UUID,String> before=ProjectionModelChanges.visualModels(feed(display,"voxel:bench",part,"voxel:cushion"),"furniture");
        Map<UUID,String> dyed=ProjectionModelChanges.visualModels(feed(display,"voxel:bench_red",part,"voxel:cushion"),"furniture");
        assertEquals(Collections.singleton(display),ProjectionModelChanges.changed(before,dyed));
        assertTrue(ProjectionModelChanges.changed(before,before).isEmpty());
        assertTrue("First snapshot re-sends nothing",ProjectionModelChanges.changed(Collections.<UUID,String>emptyMap(),dyed).isEmpty());
        UUID placed=UUID.randomUUID(),placedPart=UUID.randomUUID();
        Map<UUID,String> withPlacement=new java.util.HashMap<UUID,String>(before);
        withPlacement.putAll(ProjectionModelChanges.visualModels(feed(placed,"voxel:bench",placedPart,"voxel:cushion"),"furniture"));
        assertEquals("Newly placed displays are re-sent once the feed lists them",
                new HashSet<UUID>(Arrays.asList(placed,placedPart)),ProjectionModelChanges.changed(before,withPlacement));
        assertTrue("Removed entities are despawned normally",ProjectionModelChanges.changed(before,Collections.<UUID,String>emptyMap()).isEmpty());
    }
}
