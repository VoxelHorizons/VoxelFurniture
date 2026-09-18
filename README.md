# VoxelFurniture

VoxelFurniture is the entity-backed furniture addon for [VoxelCore](https://github.com/VoxelHorizons/VoxelCore).
VoxelCore owns content identity, custom items and resource-pack models. VoxelFurniture owns placement,
rendering, interaction, persistence and removal of furniture in the world.

## Supported renderers

- Minecraft 1.19.4 and newer: `ItemDisplay` plus an `Interaction` hitbox.
- Minecraft 1.12 through 1.19.3: invisible armor-stand fallback.
- `renderer: AUTO` selects the best available implementation at runtime.

Folia is not supported. Paper is detected naturally through the Bukkit API without making Paper mandatory.

## Content format

Furniture is declared on an ordinary VoxelCore item. The same item is placed, rendered and dropped by default:

```yaml
items:
  oak_chair:
    material: minecraft:paper
    display_name: "&6Oak Chair"
    render:
      model: voxel:furniture/oak_chair

    properties:
      furniture:
        renderer: auto
        hitbox:
          width: 1.0
          height: 1.2
        scale: 1.0
        rotation_step: 45
        offset:
          x: 0.0
          y: 0.0
          z: 0.0
```

Optional `model_item` and `drop` values can point to different VoxelCore item IDs. Explicit `display` definitions
are rejected on servers without display entities; `auto` falls back safely.

## Commands

- `/vf` - show runtime counts.
- `/vf list` - list furniture definitions.
- `/vf give <namespace:id> [amount]` - give a furniture item.
- `/vf remove <instance-uuid>` - remove a persisted instance without a drop.

## API events

Plugins can listen for `FurniturePlaceEvent`, `FurnitureBreakEvent`, and `FurnitureInteractEvent`. Place and break
events are cancellable, providing integration points for protection and gameplay addons.

## Building

VoxelCore `1.0-SNAPSHOT` must be installed in the local Maven repository first:

```text
mvn -f ../VoxelCore/pom.xml install -DskipTests
mvn clean verify
```

The output is a universal Java 8-compatible jar under `target/`.
