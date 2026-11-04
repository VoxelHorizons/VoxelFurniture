# Animation states and typed inventories

These options are declared under an item's `properties.furniture`. Animation targets are
**renderable VoxelCore item IDs**, including abstract items that inherit the furniture
definition and override their `render.model`.

## Park bins: automatically switch when full

```yaml
items:
  oak_wooden_park_bin:
    material: OAK_LOG
    render:
      model: voxel:furniture/bin/oak_wooden_park_bin
    properties:
      furniture:
        inventory:
          title: '&fPark Bin'
          size: 9
        animation:
          full: voxel:oak_wooden_park_bin_filled
  oak_wooden_park_bin_filled:
    extends: oak_wooden_park_bin
    abstract: true
    render:
      model: voxel:furniture/bin/oak_wooden_park_bin_filled
```

`animation.full` is active when **every inventory slot contains an item** (a partially
filled stack still occupies a slot). The model changes immediately after the player
puts in or takes out an item, even while the inventory remains open. Once a slot is
empty, the furniture returns to its normal or `animation.use` model.
`animation.full` takes priority over an open-inventory use animation.

## Sinks: manual toggle without storage or commands

```yaml
items:
  modern_sink:
    material: PAPER
    render:
      model: voxel:furniture/kitchen/sink/modern
    properties:
      furniture:
        animation:
          toggle: voxel:modern_sink_flowing
  modern_sink_flowing:
    extends: modern_sink
    abstract: true
    render:
      model: voxel:furniture/kitchen/sink/modern_flowing
```

Right-click switches between base and toggle models. No `animation.use`,
`inventory` or `interaction.commands` is needed. The toggled state persists
on the placed instance. The existing `setUseAnimation(instance, boolean)` and
`toggleUseAnimation(instance)` APIs also control this state.

## Ovens: smoker UI, open animation and active-burning variants

```yaml
items:
  oven:
    material: PAPER
    render:
      model: voxel:furniture/kitchen/oven
    properties:
      furniture:
        animation:
          use: voxel:oven_open
          active: voxel:oven_active
          use_active: voxel:oven_open_active
        inventory:
          type: SMOKER
          title: '&fOven'
  oven_open:
    extends: oven
    abstract: true
    render:
      model: voxel:furniture/kitchen/oven_open
  oven_active:
    extends: oven
    abstract: true
    render:
      model: voxel:furniture/kitchen/oven_active
  oven_open_active:
    extends: oven_open
    abstract: true
    render:
      model: voxel:furniture/kitchen/oven_open_active
```

The two visual axes are independent:

| Inventory | Fuel currently burning | Rendered model |
|---|---|---|
| Closed | No | `oven` |
| Open | No | `oven_open` |
| Closed | Yes | `oven_active` |
| Open | Yes | `oven_open_active` |

`animation.active` displays when the smoker's remaining burn ticks are positive;
`animation.use_active` displays when it is both burning and open. The state
updates as soon as fuel ignites or burns out, even if no inventory slots change
and no players are viewing the inventory. The usual `animation.close_delay`
still applies, including while burning.

These options are optional for existing smokers. `animation.active` requires
`inventory.type: SMOKER`; `animation.use_active` additionally requires
`animation.use` and `animation.active`. If `animation.use_active` is
omitted, an open smoker continues showing the ordinary `animation.use` model,
even when lit. For dyeable model variants, each variant must override
`render.rule.model.id` as well as `render.model`.

`inventory.type: SMOKER` creates a native **3-slot smoker-shaped inventory**
(input, fuel, output) on Minecraft 1.14+. Omit `size` (or specify 3).
A server-side smoker process consumes fuel, processes registered smoking recipes
and updates the output, including while viewers have closed the UI and the
furniture's origin chunk remains loaded. Its inventory contents are persisted.
The `animation.use` model displays while the inventory is open, and closes
according to `animation.close_delay`.

A virtual inventory has no physical smoker block entity. VoxelFurniture runs
registered smoking recipes and consumes fuel itself, and updates the open
smoker's flame and cook-arrow indicators using Bukkit's public FurnaceView
API. The oven's active models follow the same remaining fuel ticks.
Experience-orb behaviour is not implemented by the virtual block entity.
Smoker fuel/cook progress resets on server restart, although stored items
are retained.

## Compatibility

- `inventory.type` defaults to `CHEST` for existing content; these inventories
  still require `size: 9..54` in multiples of nine.
- Only `CHEST` and `SMOKER` inventory types are supported by this change.
- `animation.use` retains its previous behaviour. On non-inventory furniture
  it can still toggle for legacy content, but new toggle-only content should
  declare `animation.toggle`.
- `animation.use` and `animation.toggle` cannot both be specified on one item.
- `animation.toggle` is for non-inventory furniture.
- For tinted models whose `render.rule.model.id` points to the parent model,
  override that ID in each abstract variant to reference the variant's model.
