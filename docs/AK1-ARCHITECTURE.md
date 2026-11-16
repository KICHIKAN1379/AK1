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


## 11. Repository boundaries — 2026-11-16

The offline data layer now uses an incremental ports-and-adapters boundary:

- `AppRepository` remains the compatibility facade used by existing screens and application flows.
- `RepositoryState` owns the in-memory ring collections; domain repositories do not own duplicate copies.
- `AppPersistence` remains the serialization boundary and delegates to `LocalStore`, preserving the existing backup schema and migration behavior.
- `AppStatePublisher` publishes screen-oriented state snapshots after persistence; Compose state collection is scoped to the page that needs it rather than the root navigation composable where possible.
- Member, economy, mission, session, catalog, wheel, assistant, and catalog-bootstrap operations depend on focused internal port interfaces rather than the concrete `AppRepository` class.
- Cross-domain mutations are exposed as explicit economy commands on the ports that need them; repositories no longer call methods on the concrete facade.

### Compatibility and invariants

This migration intentionally preserves:

- Existing facade method names and call signatures used by UI code.
- Existing domain repository behavior and persistence timing.
- The current local JSON/backup format and migration rules.
- Existing member monitoring, birthday, mission, economy, and reward rules.
- Existing navigation and approved UX decisions.

The mutable collection surface is retained temporarily for compatibility. New code should use repository operations rather than mutating collections directly. A later migration can replace these collection ports with read-only query snapshots and command methods after call sites have been inventoried and covered by tests.

### Next hardening steps

1. Move startup catalog synchronization and first-run orchestration out of the `AppRepository` constructor into an explicit initializer.
2. Replace mutable collection access with read-only query snapshots and transactional commands.
3. Define persistence failure handling and serialization ownership for multi-domain mutations.
4. Add repository contract tests and exercise the complete offline import/export path.
