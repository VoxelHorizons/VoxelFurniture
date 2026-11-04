package org.voxelhorizons.furniture;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** Seat choice stays nearest and skips individually occupied anchors. */
public class FurnitureMultiSeatTest {
    @Test public void choosesNearestOfFourPicnicTableSeats() {
        List<Location> positions = Arrays.asList(
                new Location(null, -0.75, 0, -0.65),
                new Location(null, 0.75, 0, -0.65),
                new Location(null, -0.75, 0, 0.65),
                new Location(null, 0.75, 0, 0.65));
        Map<Integer, ArmorStand> active = new HashMap<Integer, ArmorStand>();
        assertEquals(1, FurnitureManager.nearestFreeSeat(
                positions, new Location(null, 0.9, 0, -0.7), active));
        assertEquals(2, FurnitureManager.nearestFreeSeat(
                positions, new Location(null, -0.9, 0, 0.7), active));

        active.put(1, occupiedStand());
        assertEquals(3, FurnitureManager.nearestFreeSeat(
                positions, new Location(null, 0.9, 0, -0.7), active));

        active.put(0, occupiedStand());
        active.put(2, occupiedStand());
        active.put(3, occupiedStand());
        assertEquals(-1, FurnitureManager.nearestFreeSeat(
                positions, new Location(null, 0.9, 0, -0.7), active));
    }

    @Test public void vacatedSeatBecomesAvailableAgain() {
        List<Location> locations = Collections.singletonList(new Location(null, 1, 2, 3));
        Map<Integer, ArmorStand> active = new HashMap<Integer, ArmorStand>();
        active.put(0, vacantStand());
        assertEquals(0, FurnitureManager.nearestFreeSeat(
                locations, new Location(null, 1, 2, 3), active));
    }

    private static ArmorStand occupiedStand() { return stand(true); }
    private static ArmorStand vacantStand() { return stand(false); }

    private static ArmorStand stand(final boolean occupied) {
        return (ArmorStand) Proxy.newProxyInstance(ArmorStand.class.getClassLoader(),
                new Class<?>[] {ArmorStand.class}, (proxy, method, args) -> {
                    if ("isValid".equals(method.getName())) return true;
                    if ("getPassengers".equals(method.getName())) return occupied
                            ? Collections.<Entity>singletonList(null) : Collections.<Entity>emptyList();
                    return null;
                });
    }
}
