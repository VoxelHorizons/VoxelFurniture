package org.voxelhorizons.furniture;

import org.bukkit.Location;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furniture.model.FurnitureRendererType;
import org.voxelhorizons.furniture.model.FurnitureSeatDefinition;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FurnitureSeatTest {
    @Test public void rotatesSeatOffsetWithFurnitureYaw() {
        FurnitureInstance instance = new FurnitureInstance(UUID.randomUUID(),
                ContentID.parse("voxel:chair", "voxel"), new Location(null, 10.5D, 64.0D, 20.5D),
                90.0F, FurnitureRendererType.DISPLAY, Collections.<UUID>emptyList(),
                Collections.emptyList());

        Location seat = FurnitureManager.seatLocation(instance,
                new FurnitureSeatDefinition(1.0D, -1.1D, 0.0D, 15.0F));

        assertEquals(10.5D, seat.getX(), 0.0001D);
        assertEquals(62.9D, seat.getY(), 0.0001D);
        assertEquals(21.5D, seat.getZ(), 0.0001D);
        assertEquals(105.0F, seat.getYaw(), 0.0001F);
    }
}
