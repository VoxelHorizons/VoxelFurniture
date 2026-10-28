# Native furniture highlighting

VoxelFurniture supports glowing outlines without PacketEvents, ProtocolLib,
NMS or third-party protocol plugins. Highlight settings inherit through
VoxelCore item `extends` just like all other nested furniture properties.

```yaml
properties:
  furniture:
    renderer: display
    highlight:
      enabled: true
      hover: false
      line_of_sight: true
      range: 16
      color: "#FFFFFF"
      include_parts: false
```

## Highlight modes

Both flags are independent:

| hover | line_of_sight | Result |
|---|---|---|
| false | false | ALWAYS: normal global glowing, subject to native display view range; can show through blocks |
| true | false | HOVER: glowing only when the player aims at the visible model or hitbox, even through blocks |
| false | true | PROXIMITY + VISIBILITY: all nearby furniture within `range` glows only for players with unobstructed lines of sight |
| true | true | HOVER + VISIBILITY: only the nearby, aimed-at furniture glows, provided blocks do not obstruct the crosshair |

`hover` and `line_of_sight` both default to `true` when `highlight`
is present. Use `enabled: false` or omit `highlight` to disable glowing.

`range` (default 6 blocks) limits activation of HOVER and PROXIMITY modes.
It is capped by the furniture's `view_distance`. ALWAYS uses native display
viewing distance and does not use `range`. `color` is `#RRGGBB`, default
`#FFD700`.

`include_parts: false` highlights the main ItemDisplay only. With `true`,
additional ItemDisplay parts are included. The Interaction hitbox is never
outlined. TextDisplay parts remain visible normally when hovered; client
glowing support for those parts is not guaranteed.

## Implementation

For viewer-dependent modes VoxelFurniture spawns a temporary non-persistent
hidden-by-default duplicate of the display model. The server shows it to
eligible players with Bukkit `Player.showEntity`, hiding the original
display only from those viewers with `Player.hideEntity`. This avoids
per-player metadata packets, NMS and scoreboard conflicts.

HOVER targets the visible model position and approximate scale, plus its
separate Interaction hitbox. PROXIMITY + VISIBILITY ray-traces Minecraft
block shapes to multiple points around the rendered model. Each nearby model
is checked independently, so more than one visible furniture instance can
glow at the same time. Looking directly at the model is not required in
PROXIMITY + VISIBILITY mode.

Outlines are removed when the condition stops being true, the viewer leaves,
a source entity changes, or the plugin disables. The originals never change
their global glowing state in these conditional modes.

Because Minecraft renders glowing outlines through blocks after a model
has been selected, line-of-sight sampling prevents activating *fully hidden*
furniture, but a partially visible furniture model can still have parts
of its glow visible through a nearby wall. This cannot be completely
eliminated using the vanilla glow effect.

The Java display renderer is supported on modern versions (1.19.4+).
Older armor-stand furniture continues to work without this display effect.
Bedrock rendering may need separate Geyser support for equivalent visuals.
The native overlay switch may occasionally produce a one-frame model swap.
