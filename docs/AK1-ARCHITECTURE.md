# AK1 Architecture — Foundation

## 1. Scope

AK1 is a mentor application. Each mentor has access only to their own ring.

```
Mentor
  └── Ring
      ├── Members
      ├── Sessions
      ├── Attendance
      ├── Missions
      ├── Economy
      ├── Shop / Catalog
      └── History
```

The ring name is user-defined at registration. No ring name such as "Abotorab 3" is hard-coded into the general UI.

## 2. Three separate values

### XP
Permanent progression value.
- Earned through mentor-recorded positive/negative events and defined activities.
- Determines level and member ranking.
- Spending points or diamonds never reduces XP.
- Manual level decrease also decreases XP so the member remains consistent with the requested level.

### Spendable points
Currency for mentor-defined real/educational rewards.
- Can be spent in the shop.
- Spending does not change XP, level, or ranking.

### Diamonds
Currency for personalization.
- Used for avatars and frames.
- Not deducted from XP or spendable points.
- Default rewards:
  - 0.5 diamond for every positive point awarded (two half-diamonds become one whole, spendable diamond; at most one half stays pending).
  - 10 diamonds for every newly reached level.
  - 30 diamonds for birthday.
  - configurable diamonds for individual/group missions.
  - configurable manual diamond grants with a required reason.
  - optional wheel rewards.

## 3. Level formula

- Level 0: 0–19 XP
- Level 1: 20–49 XP
- Level 2: 50–79 XP
- Level 3: 80–109 XP
- Level 4: 110–139 XP
- Level 5: 140–169 XP
- etc.

Formula:
`level = 0` for XP < 20; otherwise `1 + floor((XP - 20) / 30)`.

Manual level decrease must record:
- previous level
- new level
- XP removed
- required reason
- timestamp
- mentor

## 4. Shop

Main categories:
- Avatars
- Frames
- Rewards
- Wheel

Avatars and frames support a "personal creation" area for mentor-uploaded assets.

Recommended source size:
- Avatar PNG: 256×256 px, transparent background preferred.
- Frame PNG: **288×288 px**, transparent center, designed as an outer ring/stroke around the avatar rather than sitting inside/overlapping the avatar itself.
- The frame is rendered slightly larger than the avatar so its visual border surrounds the avatar without covering it.
- Future animated frame: GIF at 288×288 px with the same outer-ring geometry.

Future:
- Animated GIF frames are planned.
- The data model must not assume frames are static PNG only.

Each shop item can independently define:
- price
- currency
- minimum level
- acquisition method
- event start/end
- stock
- active state

Acquisition methods include direct purchase, level unlock, wheel-only, manual, event, and mission.

## 5. Wheel

The wheel is free to spin by default. The mentor can switch its mode:
- `FREE` — no cost.
- `POINTS` — each spin costs spendable points.
- `POINTS_AND_DIAMONDS` — each spin costs spendable points and diamonds.

The mentor defines its contents manually, for example:
- +10 points
- +5 diamonds
- free trip
- half-price tuition
- avatar
- frame
- custom reward

Wheel outcomes are recorded in member history.

In `FREE` mode the wheel does not require points or diamonds to spin.

## 6. Catalog

Every reward/shop item can have:
- image
- title
- description
- acquisition instructions

The catalog can later be exported as JPEG for sharing with members. The exported image should show both the reward image and how it can be obtained.

## 7. History

Every member has a permanent history.

History must be grouped:
- Year
  - Month
    - Events

It must support filtering by event type (XP, points, diamonds, wheel, missions, purchases, etc.) so the mentor does not need to scroll through a long unstructured timeline.

Each event stores date/time, amount when applicable, reason when applicable, source, mentor, and metadata.

## 8. Calendar

All user-facing dates use the **Jalali (Shamsi) calendar**.
- Birth dates are entered as digits only (`13850715`); the form shows `1385/07/15` by itself. Persian, Arabic and Latin digits are accepted.
- Birth dates are stored as `YYYY/MM/DD` (Jalali). Older Gregorian `yyyy-MM-dd` values are still read and converted.
- A birthday falls on the same Jalali month/day every year (reminders 3 days before and on the day; 30 diamonds once per Jalali year). Esfand 30 becomes Esfand 29 in non-leap years.
- History is grouped by Jalali year and month name.
- Conversion is pure Kotlin (`domain/calendar/JalaliCalendar.kt`) and was cross-checked against ICU's Persian calendar for every day from 1299/12/24 to 1501/01/10.

## 9. Main navigation

- Home
- Members
- تراشکاری
- Sessions
- Competition (ranking only)
- Store
- Settings

Home contains mentor-selectable shortcuts.

## 10. Offline / online

Offline:
- no password required after local setup
- export ring data from Settings
- import the versioned export later to restore the ring

Online:
- ring name + ring username + password are used for authentication
- mentor sees only their own ring

Exports must be versioned for future migrations.


## 11. Repository boundaries — 2026-11-18

### Ownership and ports

- `RepositoryState` is the in-memory source of truth. Its mutable lists remain private to the data-layer implementation and state publisher.
- `AppRepository` is a compatibility facade: it loads persisted state, exposes the existing application commands, persists mutations, and publishes screen-oriented snapshots. It no longer performs first-run catalog orchestration in its constructor.
- Domain repositories depend on focused internal ports for the collections and cross-domain commands they require; the ports no longer expose `MutableList`.
- Port collections use `RepositoryCollection<T>`: a `List` query surface with snapshot iterators and copy-on-write command operations. A command edits a detached copy and commits only after the command completes successfully. No caller receives the backing mutable list or a mutable iterator.
- Collection commands are atomic per collection. A business operation spanning several collections still follows the existing domain-repository sequence and persistence boundary; this refactor does not claim a new cross-collection transaction manager.

### Startup and catalog synchronization

- `AK1Application` is the process composition root. It creates the single `AppRepository` and invokes `AppInitializer` before any Activity or Compose UI is created.
- `AppRepository` loads the existing local state first. `AppInitializer` then coordinates starter catalog seeding, profile/rank/group pack synchronization, level-badge ownership backfill, starter-look migration, hidden-mission seeding, persistence, birthday catch-up, and assistant alert evaluation.
- The original startup order and existing idempotency markers remain in use: SharedPreferences version/seed flags, stable history IDs, Jalali-year birthday records, and the alert engine's reconciliation behavior.
- Optional synchronization steps retain their existing `runCatching` boundaries. Required first-run seeding and the final persistence call still surface failures rather than silently claiming initialization succeeded.

### Compose state subscriptions

- `AppStatePublisher` publishes immutable domain snapshots after persistence. Equal snapshots are suppressed by `StateFlow`, so an unrelated domain write does not emit a changed value for an unaffected domain.
- `AK1App` does not collect member state at the navigation root. `GrowthMemberContent` collects `membersState` only while the growth destination is composed; other destinations collect the state they display.
- `AppStatePublisherTest` verifies snapshot isolation and that mission-only changes do not replace unrelated member/session snapshots.

### Persistence and backup compatibility

- `AppPersistence` remains the serialization boundary and delegates to `LocalStore`.
- The current stored JSON schema is version 10. `LocalStore` continues to accept schema versions 1 through 10 and retains the legacy monitoring-baseline migration.
- Export preserves collection order. Import validates the candidate backup before replacing the saved payload; if parsing fails, the previous saved backup is restored. `AppRepository.importBackup` reloads the accepted state and republishes snapshots.
- This work does not change schema keys, migrations, asset restoration rules, member records, missions, economy calculations, or UX behavior.

### Contract and integration verification

- `RepositoryCollectionTest` covers the read-only query surface, detached snapshots, command ordering, and failure atomicity.
- `MemberRepositoryPortContractTest` covers member creation, archive/restore ordering, persistence calls, and the absence of a mutable-list port surface.
- `RepositoryPortsSurfaceTest` verifies every collection-valued repository port returns the read-only `RepositoryCollection` adapter.
- `ReportExportZipIntegrationTest` verifies full-ring ZIP export/import restores ordered member/history data and custom assets through the application restore path.
- `AppRepositoryBackupContractTest` covers empty backups, ordered round trips, legacy schema-9 compatibility, and rollback after invalid imports.
- `AppStatePublisherTest` continues to cover immutable snapshots and isolation of unrelated domains.

### Remaining boundary

The refactor narrows collection access and moves startup orchestration out of the repository/UI path without changing the persistence schema. Cross-domain operations still rely on the established repository call sequence and whole-state `persist()`; introducing durable multi-collection transactions or incremental persistence is outside this change's scope.
