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
        view_distance: 64
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
          rotation: 0
```

Optional `model_item` and `drop` values can point to different VoxelCore item IDs. Explicit `display` definitions
are rejected on servers without display entities; `auto` falls back safely.

### Inventories and use animations

Furniture can expose a persistent chest-style inventory. Its size must be a multiple of nine from 9 through 54.
An optional `animation.use` item selects a different render model while at least one player has that inventory open:

```yaml
items:
  oak_bench:
    material: OAK_PLANKS
    display_name: "&fOak Bench"
    render:
      model: voxel:furniture/basic/bench/oak_bench
    properties:
      furniture:
        renderer: auto
        rotation_step: 90
        animation:
          use: voxel:oak_bench_open
          close_delay: 10
        inventory:
          size: 27

  oak_bench_open:
    extends: oak_bench
    abstract: true
    render:
      model: voxel:furniture/basic/bench/oak_bench_open
```

Players with `voxelfurniture.inventory` open the inventory by interacting with the furniture. All simultaneous
viewers share the same live inventory. The use model remains active until the last viewer closes it, then the
furniture returns to its current base or blockstate model. Contents persist in `furniture.yml`, are saved on close
and shutdown, and drop when the furniture is broken. The animation model must reference a renderable VoxelCore item;
an abstract item is recommended so it cannot be obtained directly. The inventory title uses the furniture item's
resolved `display_name`, including legacy colors and VoxelCore font/UI placeholders.

`animation.close_delay` is measured in server ticks and defaults to `10` (half a second). Opening switches to the
use model immediately. After the last viewer closes the inventory, VoxelFurniture saves its contents immediately
but keeps the use model visible for this delay before restoring the normal or blockstate model. Set it to `0` for
an immediate return.

Players can place ordinary blocks against furniture collision blocks. Furniture with an inventory consumes a normal
right-click to open it, so the player must sneak while placing a block against it, matching vanilla container
interaction. VoxelFurniture items remain protected from placing their carrier blocks through this bypass.

### Render offset

`offset` is expressed in the furniture's local coordinate system rather than fixed world axes. The X/Z
components rotate around Y with the furniture's rendered yaw, while Y remains vertical.

For example:

```yaml
        offset:
          x: -0.75
          y: -1.0
          z: 0.0
          rotation: 90
```

At yaw `0`, this shifts the renderer `-0.75` on world X. At yaw `90`, the same local X offset rotates onto
world Z instead. This keeps wall-mounted and directional furniture aligned consistently when placed on different
axes.

The optional `rotation` value adds a yaw correction, in degrees, to the rendered model only. It does not rotate
the furniture placement, collision blocks, seat, or the local X/Z offset. This is useful when an authored model's
forward direction differs from VoxelFurniture's placement direction.

The local-offset behavior applies to both the ItemDisplay renderer and the legacy armor-stand renderer. Existing
persisted furniture created with the older world-axis offset behavior is detected through the renderer signature and
rebuilt once with the corrected transform when its chunk loads.

### Scale

`scale` supports both the original uniform numeric form and an axis-specific mapping.

Existing content remains valid:

```yaml
        scale: 2.0
```

which is equivalent to:

```yaml
        scale:
          x: 2.0
          y: 2.0
          z: 2.0
```

Each axis can also be controlled independently:

```yaml
        scale:
          x: 2.0
          y: 3.0
          z: 2.0
```

Missing axes in the mapping default to `1.0`, so `scale: { y: 2.0 }` only stretches the model vertically.
All scale values must be finite and greater than zero. Axis-specific scaling applies to the modern ItemDisplay
renderer; the legacy armor-stand renderer does not provide equivalent non-uniform entity scaling.

### Display view distance

`view_distance` controls the requested ItemDisplay render distance in blocks and defaults to `64`.
VoxelFurniture converts this block distance to Minecraft's native display `view_range` value when spawning modern
display furniture. The effective client-visible distance can still be limited by the server/client entity distance
settings.

```yaml
        view_distance: 128
```

This setting applies to the ItemDisplay renderer on Minecraft 1.19.4+. The legacy armor-stand renderer does not
provide an equivalent per-entity view-range setting.

### Persistence and orphan repair

Renderer entity UUIDs are persisted in `furniture.yml`, but Minecraft may not have an entity's chunk loaded when
VoxelFurniture starts. VoxelFurniture therefore never treats a failed UUID lookup during startup as proof that an old
renderer no longer exists.

On startup, persisted furniture is reconciled only when its origin chunk is loaded. VoxelFurniture also scans loaded
chunks for entities carrying its own `voxelfurniture` renderer tag. Tagged renderer entities whose UUID is no longer
referenced by any persisted furniture instance are removed as orphans. The same repair runs whenever another chunk
loads, so historical duplicate ItemDisplays/Interaction entities left by older versions are cleaned automatically as
their chunks are visited.

The orphan sweeper does **not** require a surviving furniture placement record. If a furniture instance was already
removed from `furniture.yml` while older duplicate renderer entities were left behind, those entities are still
recognized as orphans because their UUIDs are referenced by no persisted instance.

The orphan sweeper does **not** remove ordinary armor stands, display entities, NPCs, mobs, or entities owned by other
plugins. Only entities carrying VoxelFurniture's renderer tag and no longer referenced by `furniture.yml` qualify.

Administrators can run `/vf cleanup` to immediately sweep every currently loaded chunk and report the number of
orphaned renderer entities removed. Unloaded chunks are still repaired automatically when they later load.

Newly persisted furniture also stores a renderer-definition signature. If the definition still matches on the next
restart and its recorded renderer entities are present, VoxelFurniture reuses those entities rather than spawning a
new copy. A live VoxelCore content revision still forces the normal definition synchronization so configuration
changes propagate immediately.

### Live definition synchronization

Placed furniture keeps only instance-specific state such as its stable furniture UUID, world location, and placed
yaw. Definition-driven rendering is reconciled from the current VoxelCore item definition.

VoxelFurniture watches VoxelCore's published content revision. After a successful VoxelCore content reload, existing
placed furniture is automatically rebuilt from the latest definition, including:

- rendered model/item data and neighbor-selected models
- renderer choice
- uniform or per-axis scale and render offsets
- interaction hitbox dimensions
- `view_distance`
- seat configuration/position
- collision block layouts when the new cells can be migrated safely

Renderer entity UUIDs may change during a genuine reconciliation and the updated UUIDs are persisted back to
`furniture.yml`; the furniture instance UUID itself remains stable. Unloaded instances wait for their chunk to load
before being rebuilt, preventing old persisted renderers from being orphaned during startup. If an updated collision layout would overwrite
another solid block or another furniture instance, VoxelFurniture keeps that instance's previous collision blocks
and logs a warning instead of modifying unrelated world blocks.

The same reconciliation runs on plugin startup, so definition changes also apply to furniture that was placed before
the server restarted. Inventory/chest ItemStacks are owned by VoxelCore and are outside VoxelFurniture's placed-instance
synchronization.

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

Directions are world north/east/south/west. Rules are tried most-specific-first. For equally-specific
rules, an exact/unrotated pattern match is preferred over a rule that only matches after one or more quarter-turns;
remaining ties use YAML order. Each pattern rotates through 90-degree turns by default. Use `rotate: false` for
explicit inner/outer corner patterns that must represent only the directions written in YAML. `rotation` changes
the selected model yaw after a rule matches; it does not disable pattern rotation.
For directional furniture such as benches, set `relative: true` and `rotate: false` on each rule.
Directions then follow the furniture's snapped placement yaw (north at yaw 0). By default, relative rules count only
neighbors of the same concrete item ID **and facing**, preserving the original bench behavior.

Use `neighbor_facing` when a rule must distinguish the neighbour's orientation. Supported values are `SAME` (the default), `SAME_OR_CORNER`, `PERPENDICULAR`, `CLOCKWISE`, `COUNTERCLOCKWISE`, `OPPOSITE`, and `ANY`. `SAME_OR_CORNER` counts aligned neighbours plus a perpendicular neighbour only when that neighbour continues onto the other axis as a real L junction. This lets the straight arm beside a corner use its middle model without treating an unrelated sideways chair as connected. `PERPENDICULAR` selects only furniture turned 90 degrees relative to the current piece; use `CLOCKWISE` and `COUNTERCLOCKWISE` when mirrored connections need different model rotations.

Set `aligned_only: false` on a relative rule when differently facing pieces must still count as connected. This is
useful for modular seating: a straight chair next to a 90-degree corner can still become a middle segment, while
local L-shaped patterns can distinguish inner and outer corners. `aligned_only` defaults to `true`, so existing
content does not change.

The selected model retains its placement yaw; `rotation` can still add an offset for an authored model facing
differently. A left-end model with legs on its local west side therefore needs a neighbor on its local east side.
Rules without `relative: true` retain the original world-direction matching used by tables.
`absent: [direction, ...]` can prevent a rule matching extra neighbors. When no rule matches,
the base item's model and original placement yaw are retained. Only directly adjacent furniture
of the same concrete ID at the same height connects. State changes never alter collision cells.
Blockstates require `rotation_step: 90`. Collision blocks are optional and do not participate in neighbor detection.

## Commands

- `/vf` - show runtime counts.
- `/vf list` - list furniture definitions.
- `/vf give <namespace:id> [amount]` - give a furniture item.
- `/vf remove <instance-uuid>` - remove a persisted instance without a drop.
- `/vf cleanup` - remove unreferenced VoxelFurniture renderer ghosts from all currently loaded chunks.

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
