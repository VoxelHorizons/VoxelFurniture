# Bedrock storage furniture interaction safety (issue #48)

**Status:** mitigation and diagnostic instrumentation pending a real Bedrock retest. The
root cause of the original bamboo cupboard-bench right-click drop/ghost incident
has **not yet been proven**. Geyser translation is a hypothesis, not a diagnosis.

## What this branch changes

- An authenticated **Floodgate Bedrock player** must **sneak and attack**
  (crouch + left-click/punch) to intentionally break/pick up **storage furniture**.
  Ordinary non-sneaking right-clicks should open cupboards/drawers without
  dropping or removing them even if they arrive in a Bukkit break/damage path.
  This is deliberately restricted to storage pieces; Java clients and
  non-storage furniture keep their prior pickup behavior.
- This applies to collision left-click, `BlockBreakEvent`, and player-driven
  `EntityDamageByEntityEvent`. Vanilla block breaking/damage is still cancelled
  for managed furniture, and WorldGuard/Lands restrictions still apply afterward.
- `FurnitureManager.breakFurniture` rechecks the current authoritative instance
  **before dispatching** `FurnitureBreakEvent` and **again after it returns**.
  Repeated/stale callbacks cannot drop the same instance a second time.
- An optional trace logs Bedrock player name, instance UUID/definition, sneaking,
  and which Bukkit event path reached the listener. It **does not** log any
  inventory contents, link tokens, account identifiers or Geyser packets.

## Configuration

```yaml
bedrock:
  interaction:
    storage-pickup-requires-sneak: true
    trace-events: false
```

**Floodgate is optional**, discovered at runtime through its installed plugin
classloader. It is listed under `softdepend` but not required to compile or run
on Java-only servers. Without working Floodgate player detection the guard
cannot identify Bedrock sessions and is ineffective; the plugin reports that
at startup. For proxy deployments, ensure Floodgate player data is forwarded
to the backend. No ProtocolLib required.

## Test matrix

1. Install the new build, **fully restart Paper**, and note exact VoxelCore,
   Furniture, Geyser, Floodgate, Paper and Bedrock client versions.
2. In an **unprotected staging region** as non-OP Bedrock Survival, place a
   **bamboo cupboard bench** with a few disposable inventory items. Repeatedly
   tap/use to open/close its GUI **without sneaking**. Expected: inventory
   opens, no model/item drop, no persistent duplicate/ghost.
3. Without sneaking, punch both the **rendered entity** and **collision block**.
   Expected: the storage furniture remains intact. This intentional change
   protects against translated break events from a right-click.
4. Sneak + attack the bench as owner in a region where breaking is allowed.
   Expected: exactly one furniture item and the stored contents (if any) drop;
   there is no duplicate on a second hit or after relog/restart.
5. Repeat on **cherry drawer bench** (previously unaffected) and at least one
   other storage variant; verify Java normal left-click breaking and Bedrock
   non-storage breaking are unchanged.
6. Verify Hub WorldGuard DENY still blocks even sneak-attacking, and permitted
   right-click/GUI interactions remain allowed. Check both Java and Bedrock.
7. Reload the chunk, disconnect/reconnect, and restart Paper after each major
   scenario. Confirm furniture.yml state matches what is visible in world.
8. For a failing case, enable `trace-events: true`, **restart**, reproduce only
   a small number of interactions, capture `[BedrockFurnitureInput]` log lines
   and event timestamps, then disable trace. The trace may be noisy.

If the right-click still causes a ghost **with no managed BREAK/DAMAGE event
trace**, investigate vanilla carrier-block changes, Bedrock projection and
renderer persistence separately. If only a non-sneaking break is blocked but
the original event still appears, the guard mitigated the destructive effect
without identifying the source packet. Do not close #48 until both break and
restart-persistent ghost have been retested against exact client builds.
