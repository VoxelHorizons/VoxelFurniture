# WorldGuard and Lands protection integration

Both hooks are optional and enabled by default in `config.yml` under `protection.worldguard` and `protection.lands`. If either plugin is installed and active, its permission check must succeed; one plugin's ALLOW does **not** override another's DENY.

## WorldGuard

Custom region flags are registered during `onLoad()`, before WorldGuard locks its registry. Keep `WorldGuard` in `softdepend`.

| Plugin | Place | Break / pickup | Interact / use | Storage | Modify |
|---|---|---|---|---|---|
| VoxelFurniture | `voxel-furniture-place` | `voxel-furniture-break` | `voxel-furniture-interact` | `voxel-furniture-inventory` | `voxel-furniture-dye` |
| VoxelVehicles | `voxel-vehicle-place` | `voxel-vehicle-break` | `voxel-vehicle-interact` | `voxel-vehicle-inventory` | `voxel-vehicle-modify` |

### Example: visitor-friendly but protected Hub

Use the **actual region ID** and world. The example targets the global region in world `voxel_hub`; if the Hub has a named, higher-priority region, configure that region instead. These are **WorldGuard commands**, not VoxelFurniture commands:

```text
/rg flag -w voxel_hub __global__ voxel-furniture-place deny
/rg flag -w voxel_hub __global__ voxel-furniture-break deny
/rg flag -w voxel_hub __global__ voxel-furniture-dye deny
/rg flag -w voxel_hub __global__ voxel-furniture-interact allow
/rg flag -w voxel_hub __global__ voxel-furniture-inventory allow
```

When inventory access should follow interactions, you can **unset** `voxel-furniture-inventory` instead of explicitly allowing it. A dedicated inventory DENY always takes precedence; an explicit vanilla WorldGuard `chest-access deny` still blocks storage. In the Hub above, ordinary visitors can sit, toggle animated furniture and open its public inventory, but not place, break or recolor furniture.

**Important correction:** WorldGuard's `RegionQuery.testBuild(location, player, customFlag)` lets an explicit custom ALLOW override the vanilla BUILD restriction. To stop that from inadvertently authorizing physical furniture mutations, VoxelFurniture now requires **both** vanilla BUILD permission and no explicit `voxel-furniture-place/-break/-dye DENY` for placement, breaking and recoloring. Explicit custom ALLOW does **not** override vanilla BUILD DENY for these actions. This differs intentionally from WorldGuard's legacy `testBuild` override semantics. WorldGuard's bypass permissions still apply for deliberately authorized staff. Interaction and inventory flags retain their ability to allow activity in build-protected areas.

The flag management UI may not expose all selectable states. Try `/rg flag -w <world> <region> voxel-furniture-place deny` directly and review the command response and `/rg info -w <world> <region>`. If GitHub build and plugin enable both succeed but the command reports `Unknown flag`, check that WorldGuard is loaded **before** VoxelFurniture and that custom flags were registered during `onLoad()`. If it reports insufficient permissions, the issuer needs the relevant `worldguard.region.flag.*` permission. Recheck higher-priority overlapping regions; `__global__` is not automatically the effective region at the furniture position.

For player-event cancellation, VoxelFurniture **never revives a cancelled placement** (the cancelling plugin cannot be reliably identified). It may process an explicitly permitted furniture interaction when WorldGuard cancelled the vanilla interaction, but does not change the vanilla event to ALLOW. Both WorldGuard and Lands must separately permit the operation when enabled.

## Lands permissions

Lands uses its native role checks, including trusted roles and sub-areas, via the public API when installed.

| Custom action | Lands role flag |
|---|---|
| Furniture/vehicle placement | `BLOCK_PLACE` |
| Furniture/vehicle removal | `BLOCK_BREAK` |
| Furniture/vehicle storage access | `INTERACT_CONTAINER` (treated as `CHEST`) |
| Furniture animation, seating or commands | `INTERACT_GENERAL` |
| Vehicle riding/mounting | `VEHICLE_USE` |
| Vehicle chest installation | `BLOCK_PLACE` |
| Furniture recoloring or vehicle chest removal | `BLOCK_BREAK` |

A Lands-denied inventory cannot be opened just because WorldGuard grants `voxel-*-inventory`. Conversely, a Lands-trusted player must also satisfy the WorldGuard checks where WorldGuard protects the same coordinates.

VoxelVehicles also retains its existing per-vehicle owner UUID and `publicAccess` logic. A Lands member cannot override a private vehicle's personal owner access rule.

## Manual acceptance test matrix

1. Install **neither** plugin: existing furniture/vehicle behavior remains intact.
2. Install **WorldGuard only**: deny vanilla build; explicitly deny furniture place, break and dye, allow furniture interact and inventory in Hub. Verify non-OP seats, animation and storage work but place/break/dye do not. Vehicle flags require their own implementation/tests in VoxelVehicles; do not assume this PR fixes vehicles.
3. Set each custom flag to ALLOW, DENY and UNSET independently; verify overlapping region priorities, inheritance and region members. Critically, custom place/break/dye ALLOW must **not** override vanilla BUILD DENY; explicit DENY must still block users otherwise allowed to build. Confirm an unset inventory flag inherits interact unless CHEST_ACCESS explicitly denies.
4. Install **Lands only**: an untrusted player cannot place/break furniture or use protected furniture inventory. Grant appropriate Lands role flags and verify permitted interaction without broad placement rights. Vehicles are a separate addon.
5. Install **both**: denial by either provider wins.
6. Multi-block furniture crossing a claim/region boundary must not be placed or dismantled through a neighboring permitted region.
7. Verify the VoxelFurnitureShop controlled-fixture visitor restrictions remain effective, and shop editor right-click block placement is still possible.
8. Confirm ordinary block and chest use is never newly permitted just because a custom furniture flag is enabled. Repeat checks as a **non-OP Survival** player, an owner/member, an admin with WorldGuard bypass and a Bedrock player. Capture logs and active region state.

These are integration tests to carry out on a test server; GitHub Actions compilation cannot simulate external WorldGuard/Lands runtime region behavior.
