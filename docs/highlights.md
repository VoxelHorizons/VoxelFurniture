# Native furniture highlighting

VoxelFurniture supports glowing outlines without ProtocolLib, PacketEvents, NMS or other
packet dependencies. The feature is opt-in per furniture item.

```yaml
properties:
  furniture:
    renderer: display
    hitbox:
      width: 1.0
      height: 1.0
    highlight:
      enabled: true
      hover: true
      color: "#FFD700"
      range: 6
      line_of_sight: true
      include_parts: false
```

- `enabled` defaults to `true` when a highlight section is present; omit the
  section or set `enabled: false` to disable the effect.
- `hover: true` (default) outlines the model only for the player whose
  crosshair points inside its **visible display proxy or Interaction hitbox**.
  The visual target uses the model's current entity position and scales up
  with the model; furniture render offsets and bob/spin animation do not
  require manual hitbox changes.
- `hover: false` permanently glows for everybody via the normal Bukkit
  entity effect. Vanilla glow can be seen **through walls** in this mode.
- `color` is an RGB `#RRGGBB` value (default `#FFD700`).
- `range` limits hover detection in blocks (default `6`). It also respects
  the furniture's smaller `view_distance`.
- `line_of_sight: true` (default) uses native block-shape ray tracing, so
  HOVER is not activated when a wall obstructs the player's crosshair.
  This option applies only to HOVER.
- `include_parts: false` (default) highlights just the main ItemDisplay.
  With `true`, additional **ItemDisplay** parts are also highlighted;
  TextDisplay parts stay visible normally when hovering, because reliable
  client-side text glowing cannot be guaranteed.

## How HOVER works

There is **no global glow state change** for hover. VoxelFurniture spawns
a temporary, non-persistent, hidden-by-default copy of the targeted ItemDisplay,
shows it only to that viewer using Bukkit's `Player.showEntity`, and
hides the original display only from that viewer with `Player.hideEntity`.
The interaction hitbox stays present. Other players always see the original
unchanged furniture. Copies are removed when the player looks away, the source
entity changes, the player leaves, or the plugin disables.

This feature currently targets the Java display-entity renderer (1.19.4+).
The 1.12.2 armor-stand renderer continues working as before and does not
offer the same display-model outline. Bedrock rendering depends on Geyser
and its client-side support; the Java glow appearance should not be assumed
to transfer directly to Bedrock.

Because hover replaces a rendering entity for only one viewer, it can
occasionally cause a one-frame model swap when targeting a box. No scoreboard
changes or external packet libraries are used.
