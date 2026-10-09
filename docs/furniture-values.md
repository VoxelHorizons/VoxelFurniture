# Furniture item values

Add `value` (or `worth`) to a furniture item's inherited `properties.furniture` section:

```yaml
items:
  oak_chair:
    material: PAPER
    properties:
      furniture:
        renderer: display
        value: 250
```

Furniture values are nonnegative finite numbers; missing value means **not for sale**, rather than a zero-cost item. Child items may override inherited value metadata. `FurnitureManager.value(ContentID)` and `FurnitureManager.value(FurnitureInstance)` return `OptionalDouble`, for use by VoxelFurnitureShop and other integrations. `displayName(ContentID)` exposes the configured item name. VoxelFurniture works without Vault.

**Economy distinction:** Vault supplies balance/withdraw/deposit services, not a global item worth registry. The value is now available to integrations; Essentials `/worth` and ShopGUI+ pricing are **not automatically overridden** by this PR because those integrations have their own pricing systems. Those require an explicit bridge. The furniture shop uses the same value as display-only metadata until purchasing is implemented.
