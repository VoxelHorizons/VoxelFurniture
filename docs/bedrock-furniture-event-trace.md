# Model-specific Bedrock furniture interaction investigation — #48

## Expected behavior (Java/Bedrock parity)

**No new input requirements.** Right-click/tap a storage furniture model to
open its inventory, left-click/attack to pick up or break it if authorized.
Existing crouching, placement and edit-mode behavior remain unchanged on both
editions. Never require sneak-to-break on Bedrock.

The owner has observed an intermittent Bedrock-only break/drop and sometimes
a restart-persistent ghost when right-clicking a **bamboo cupboard bench**.
A **cherry drawer bench** was unaffected in side-by-side tests. This suggests
a model/definition, hitbox/collision or event routing difference, but does
**not** establish the root cause or implicate Geyser specifically.

## Implementation in this PR

1. **No behavioral suppression of Bedrock inputs.** Identical Java/Bedrock
   action semantics are retained.
2. An optional, **read-only** event trace helps identify whether an input
   arrived as a collision block click, carrier block break, entity damage,
   or entity right-click. Each event reports the *actual furniture definition*
   and instance, renderer and collision-block count, cancellation state,
   clicked block/entity type and hand where applicable. It does **not** log
   inventory contents or player UUIDs.
3. `FurnitureManager.breakFurniture` resolves the live instance by UUID
   before and after `FurnitureBreakEvent`, preventing a repeated/stale
   event from dropping the same furniture more than once. This changes no
   successful one-time pickup path for either edition.

## Definition comparison: owner-provided benches(5).yml (120 entries)

The provided content resolves via `extends`; there is no obvious malformed
furniture property in the affected entry. These four examples isolate two
possible differences without changing the normal controls:

| Furniture ID | Inherited base | Material | Hitbox | 27-slot inventory | Open animation |
| --- | --- | --- | --- | --- | --- |
| `bamboo_cupboard_bench` (**affected**) | `oak_cupboard_bench` | `BAMBOO_PLANKS` | **1.0 × 1.0** | Yes | Yes |
| `cherry_drawer_bench` (**working**) | `oak_drawer_bench` | `CHERRY_PLANKS` | **0.5 × 0.5** | Yes | Yes |
| `cherry_cupboard_bench` | `oak_cupboard_bench` | `CHERRY_PLANKS` | **1.0 × 1.0** | Yes | Yes |
| `bamboo_drawer_bench` | `oak_drawer_bench` | `BAMBOO_PLANKS` | **0.5 × 0.5** | Yes | Yes |

All four inherit `renderer: auto`, `scale: 2.0`, `rotation_step: 90`,
one collision block at 0/0/0, and opening-model references. The
`DisplayFurnitureRenderer` uses these hitbox values directly to set the
server-side `INTERACTION` entity's width/height. The effective base
collision block material comes from the parser, not the top-level item
`material`; these are separate concepts.

**Do not change the cupboard hitbox solely because it is larger.** The
model geometry and click events must establish whether the size contributes
to this incident. We have not tested the other cupboard variants.

### Minimal four-model live test

Use an admin account to obtain these items, then **switch to a non-OP
Bedrock Survival account** to place and right-click each in a test region:

```text
/vf give voxel:bamboo_cupboard_bench
/vf give voxel:cherry_drawer_bench
/vf give voxel:cherry_cupboard_bench
/vf give voxel:bamboo_drawer_bench
```

Use the same ordinary right-click/tap on each (do not sneak). Record
**works / unexpectedly drops / leaves ghost** for each. This distinguishes
a large-hitbox/cupboard issue from a bamboo-specific or individual-model
issue. Capture a few `[FurnitureInteractionTrace]` lines only if the
failing difference remains reproducible.

## Enable a short trace for one furniture definition

In `plugins/VoxelFurniture/config.yml`:

```yaml
debug:
  interaction-trace:
    enabled: true
    definition: 'voxel:bamboo_cupboard_bench'
```

The ID shown is an **example** based on the owner's furniture naming; replace
it with the exact deployed item/definition ID. Fully restart the server for
this listener configuration to take effect. Briefly reproduce the bug with
a non-OP Bedrock Survival client. Save nearby `[FurnitureInteractionTrace]`
log lines and relevant server timestamps, then set the filter to the actual
cherry drawer bench definition ID and repeat the same interaction sequence.

If needed, set `definition: ''` to trace all furniture, but do so only
briefly because it can be noisy. Disable tracing afterward and restart.

## Diagnosis and release acceptance

- Compare **both actual definitions** (not only model names): collision-block
  material/positions, placement rules, renderer, hitbox geometry and offset,
  inventory, model animation and display parts.
- On Bedrock and Java, repeat ordinary right-click inventory opening, normal
  left-click removal, crouch + placement behavior and inventory close/reopen
  without changing control requirements.
- Inspect event order and cancellation: `PlayerInteractEvent`,
  `BlockBreakEvent`, `EntityDamageByEntityEvent`, and
  `PlayerInteractEntityEvent`.
- Verify whether the ghost is still in the authoritative `furniture.yml`
  registry, still has server entities/collision blocks, or is only visible
  in the Bedrock client after a reconnect. The distinction determines whether
  the fix belongs in the plugin's state/cleanup or the Geyser projection.
- Test disposable storage inventory content, chunk unload/reload and server
  restart, ensuring exactly one item/content drop after deliberate left-click,
  no accidental drops and no persistent ghost.
- Preserve WorldGuard/Lands place/break/interact behavior, Java parity,
  FurnitureShop edit mode and optional dependencies.
- Do **not** describe this PR as a resolved root-cause fix until the owner
  reproduces and confirms the correct event/model-specific correction.

Issue: https://github.com/VoxelHorizons/VoxelFurniture/issues/48
