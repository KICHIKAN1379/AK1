# AK1 Architecture Refactor — MRT Data Layer

Status: Stage 1 (architecture inventory and target design)
Base branch: `redesign/home-command-center`
Working branch: `refactor/mrt-domain-state`
Base commit: `44f68767fee68a7f8889c05694efa543b175e192`

## Scope

This refactor is limited to two technical concerns:

1. Split the responsibilities currently concentrated in `data/AppRepository.kt`.
2. Replace the root-level `key(refresh)` remount strategy in `ui/screens/AK1App.kt` with observable, domain-scoped state updates.

The current screen layouts, visual design, navigation labels, and UI/UX decisions are out of scope. The new MRT information architecture is the product-domain map used to group data and operations; this work must not silently implement a visual redesign or move screens.

## Verified baseline

- `AppRepository.kt`: 1,261 lines; one class owns loading, starter catalog seeding, asset-pack synchronization, member/group operations, reading, economy, mission workflows, sessions, attendance, shop, wheel, history, persistence, and backup import/export.
- `AK1App.kt`: uses a mutable integer `refresh`, a `changed()` callback, and `key(refresh)` around the active destination content. A refresh can therefore discard remembered UI state by replacing the keyed composition subtree.
- UI files directly read mutable repository collections (for example `repo.members`, `repo.history`, `repo.shop`, `repo.missions`, `repo.sessions`, and `repo.attendance`). The migration must account for these reads; simply deleting `key(refresh)` would leave screens without a reliable observable update contract.
- `LocalStore.kt` serializes and restores the complete local state using backup schema version 9. It is a critical compatibility boundary.
- Existing domain services and tests already cover several pure rules, including economy, history, ranking, shop, wheel, reading, and Jalali date rules.

## Target architecture

### 1. Application composition root

Keep a small application-level facade for construction and dependency wiring. It must not own business rules or mutable domain collections. During migration, retain a compatibility facade so existing screens can be migrated incrementally without a risky all-at-once rewrite.

### 2. Domain repositories

Group operations by durable domain responsibility, not by screen:

- Ring/account: ring identity and initialization.
- Members and groups: member lifecycle, group membership, archive/restore, profile data.
- Economy and history: XP, spendable points, diamonds, ledger events, level transitions.
- Missions: mission definitions, completions, hidden missions, member requests, mentor notices.
- Learning: reading books and derived reading progress.
- Sessions and attendance: session lifecycle and attendance records.
- Catalog and assets: shop items, custom assets, profile packs, ownership and equipment.
- Wheel: configuration, eligibility, spin and reward application.

Where two domains must change atomically (for example, an economy change plus its history event), expose one operation that commits both together. Do not split transactions merely to create more classes.

### 3. Observable state

Expose immutable snapshots through domain-scoped observable streams. A successful domain mutation must:
1. validate the operation;
2. update the domain state;
3. persist the complete compatible local state;
4. publish only the affected domain snapshots (and any dependent domain snapshots).

Do not publish partially applied state. Avoid a single global revision as the permanent screen-update mechanism. Compose screens should collect only the domains they actually display.

### 4. Persistence boundary

Preserve `LocalStore` schema version 9 and existing import/export compatibility during the repository refactor. Do not change JSON keys, defaults, asset restoration rules, or migration behavior as incidental cleanup. Persistence extraction can be incremental, but the storage codec must remain the single source of truth until migration tests prove compatibility.

### 5. Compose integration

Remove the remount-based `key(refresh)` mechanism only after observable state is wired into all affected destinations. Replace `changed()` with explicit mutation results/state publication; preserve local form state, selected tabs, dialogs, scroll positions, and navigation state across data updates.

## Migration sequence

1. **Architecture inventory and design** — this document; establish domain boundaries, invariants, and the working branch.
2. **Repository decomposition** — introduce domain repository boundaries and move business operations behind them while retaining a compatibility facade.
3. **Observable domain state** — add immutable per-domain snapshots and publish changes only after successful persistence.
4. **Compose migration** — migrate screens to collect their required domain state, then remove `key(refresh)` and obsolete refresh callbacks.
5. **Verification and hardening** — add/extend repository and state-publication tests; verify backup round trips, domain invariants, Build, and Unit Tests in GitHub Actions.

## Non-negotiable invariants

- XP, spendable points, and diamonds remain distinct values with their existing rules.
- Every economy mutation and corresponding history event remain atomic.
- Existing mission completion/request/notice behavior and hidden-mission rules remain intact.
- Member/group references remain valid through delete, archive, and restore operations.
- Session deletion and attendance cleanup preserve current semantics.
- Shop ownership, purchase, equipment, stock, currency, and level-unlock rules remain unchanged.
- Wheel costs, eligibility, weighted selection, and reward application remain unchanged.
- Jalali date behavior remains unchanged.
- Backup schema version 9 remains readable and writable; import/export must preserve custom assets and all current domain data.
- Offline-first behavior remains; do not enable cloud sync or introduce a backend.
- No visual redesign or unrelated feature changes are included.

## Acceptance criteria

- No business logic is left concentrated in a single all-domain repository.
- Domain operations have clear ownership and transaction boundaries.
- UI destinations observe relevant domain changes without remounting the entire destination subtree.
- A mutation in one domain does not force unrelated destinations to rebuild.
- Existing local data and ZIP/JSON backup compatibility are preserved.
- Build and all unit tests pass on the refactor branch; failures are reported with their actual workflow evidence.
