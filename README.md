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
        placement: TOP
        seat:
          x: 0.0
          y: -1.1
          z: 0.0
          yaw: 0.0
        offset:
          x: 0.0
          y: 0.0
          z: 0.0
```

Optional `model_item` and `drop` values can point to different VoxelCore item IDs. Explicit `display` definitions
are rejected on servers without display entities; `auto` falls back safely.

### Placement faces

`placement` controls which face of a block can be clicked to place the furniture. It defaults to `TOP` for
backwards compatibility:

- `TOP` - only the top face of a block (`UP`)
- `BOTTOM` - only the underside of a block (`DOWN`)
- `SIDE` - any horizontal face (`NORTH`, `EAST`, `SOUTH`, or `WEST`)
- `ALL` - top, bottom, or any horizontal side

Once an item is recognized as furniture, VoxelFurniture cancels the vanilla item interaction even when placement is
not allowed or cannot complete. This prevents the item's carrier material from being placed accidentally when, for
example, a `TOP`-only chair is right-clicked against the side of a block.

### Seats

Furniture can opt into right-click seating with a `seat` mapping. If `seat` is absent, interaction behavior is
unchanged and the furniture cannot be sat on.

```yaml
        seat:
          x: 0.0
          y: -1.1
          z: 0.0
          yaw: 0.0
```

The seat offset is local to the furniture and rotates with its placement yaw. `x` and `z` move the seat around
the furniture model, `y` controls the seated height, and `yaw` adds an optional facing offset to both the
seat anchor and the rider. Use values such as `yaw: 180` when a chair model is authored facing the opposite
direction. Defaults are `x: 0`, `y: -1.1`, `z: 0`, and `yaw: 0`.

Right-clicking seated furniture first fires `FurnitureInteractEvent`. If another plugin cancels that event,
VoxelFurniture does not mount the player. Otherwise players with `voxelfurniture.sit` can occupy the seat when it
is free. Seats use transient invisible marker armor stands and are not written to `furniture.yml`. Empty seat
anchors are removed automatically, and active seats are removed when the furniture is broken, administratively
removed, or VoxelFurniture shuts down.

### Collision blocks

The optional `blocks` collection places real blocks with the furniture to provide physical collision. Omit
`blocks` entirely for pass-through furniture such as chairs or decorative props. Furniture identity, placement
origin, and neighbor-dependent blockstates are tracked independently from collision blocks, so collisionless
furniture still connects and refreshes normally. Offsets are whole block coordinates relative to the placement cell
and rotate with the furniture's snapped yaw. The material defaults to `BARRIER`, but another non-air block material
can be selected explicitly:

```yaml
        blocks:
          1:
            x: 0
            y: 0
            z: 0
          2:
            x: 1
            y: 0
            z: 0
            material: BARRIER
```

Both list syntax and numbered-map syntax are accepted. Placement fails without consuming the item when any resolved
cell is occupied. The exact cells and materials are stored with each placed instance, so later configuration changes
cannot make existing furniture remove unrelated blocks. Managed cells are protected from breaking, fluids, pistons,
entity block changes, and explosions. Left-clicking any managed cell breaks the owning furniture when the player has
`voxelfurniture.break`; right-clicking it fires the normal `FurnitureInteractEvent`.

On Paper versions that expose pick-item events, middle-clicking either a furniture renderer entity or one of its
managed collision blocks in Creative mode selects an existing copy of the furniture item, or creates one in the
selected hotbar slot when the player does not already carry it. Older server APIs continue to load normally without
this optional behavior.

### Neighbor-dependent models

`blockstates` on the concrete furniture definition switches the *rendered model only*. The placed furniture,
its drop and Creative middle-click remain the concrete `oak_table` item. Variant items can be `abstract: true`
when they have a material and `render.model`; VoxelCore allocates their models but still rejects giving them.
This feature requires VoxelCore with abstract render allocations and `createRenderItem` support.

```yaml
items:
  oak_table:
    material: OAK_PLANKS
    display_name: "&fOak Table"
    render:
      model: voxel:furniture/basic/oak_table
    properties:
      furniture:
        renderer: auto
        rotation_step: 90
        blockstates:
          - neighbors: [north, east, south, west]
            model_item: voxel:oak_table_middle
          - neighbors: [north, east, south]
            model_item: voxel:oak_table_middle
          - neighbors: [north, east]
            model_item: voxel:oak_table_corner
          - neighbors: [north, south]
            model_item: voxel:oak_table_middle
          - neighbors: [north]
            model_item: voxel:oak_table_end
            rotation: 0
  oak_table_end:
    extends: oak_table
    abstract: true
    render:
      model: voxel:furniture/basic/oak_table_side
  oak_table_corner:
    extends: oak_table
    abstract: true
    render:
      model: voxel:furniture/basic/oak_table_corner
  oak_table_middle:
    extends: oak_table
    abstract: true
    render:
      model: voxel:furniture/basic/oak_table_middle
```

Directions are world north/east/south/west. Rules are tried most-specific-first (ties use YAML order);
each pattern rotates through 90-degree turns by default. `rotate: false` fixes the specified direction;
`rotation` adds degrees to the matched yaw to correct a model authored facing another direction.
For directional furniture such as benches, set `relative: true` and `rotate: false` on each rule.
Directions then follow the furniture's snapped placement yaw (north at yaw 0), and only neighbors
of the same concrete item ID **and facing** count. The selected model retains its placement yaw;
`rotation` can still add an offset for an authored model facing differently. A left-end model
with legs on its local west side therefore needs a neighbor on its local east side. Rules without
`relative: true` retain the original world-direction matching used by tables.
`absent: [direction, ...]` can prevent a rule matching extra neighbors. When no rule matches,
the base item's model and original placement yaw are retained. Only directly adjacent furniture
of the same concrete ID at the same height connects. State changes never alter collision cells.
Blockstates require `rotation_step: 90`. Collision blocks are optional and do not participate in neighbor detection.

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
