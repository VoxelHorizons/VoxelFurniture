package org.voxelhorizons.furniture.render;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.UUID;

final class EntitySupport {
    private EntitySupport() {}

    static Entity find(UUID id) {
        try {
            Method method = Bukkit.class.getMethod("getEntity", UUID.class);
            return (Entity) method.invoke(null, id);
        } catch (ReflectiveOperationException ignored) {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) if (entity.getUniqueId().equals(id)) return entity;
            }
            return null;
        }
    }

    static void remove(Iterable<UUID> ids) {
        for (UUID id : ids) {
            Entity entity = find(id);
            if (entity != null) entity.remove();
        }
    }
}
