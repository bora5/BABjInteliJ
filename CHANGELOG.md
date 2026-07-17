<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# BABj Support Changelog

## [Unreleased]

### Fixed

- Agent Studio and BABj Navigator now scan PSI in non-blocking read actions instead of directly on
  the Swing event-dispatch thread, preventing `Read access is allowed from inside read-action only`
  exceptions and avoiding UI freezes on larger projects.
- Tool-window tree rendering uses immutable scan snapshots; PSI is resolved only inside guarded
  read actions when the user navigates to source.

### Changed

- Association fields whose type inherits BAB `AbstractEntity` are now detected even when their JPA
  relationship annotation is placed on the getter instead of the field.
- Association DTO/display projection now selects an actual `String` property in the order
  `naziv`, `username`, `name`, `oznaka`, then the first remaining `String` field, including
  inherited fields.
- Documented and locked in the CRUD generation contract: entity associations flatten to their
  display `String`, while enums retain their concrete DTO/Home type and use typed combo boxes
  populated with `Enum.values()` in edit windows.
- Added PSI and template regression coverage for associations and enums across scanning, DTO, Home,
  and EditWindow generation, and raised the plugin version to **1.3.1**.

### Added — complete module support

- Import scaffolds are now wired into newly generated Views through `@EnableImport` and
  `@ImportWindow`; CSV and Excel are mutually exclusive because BABj exposes one import action.
- Added typed database and file-system attachment View integration for entities implementing the
  matching BABj parent interfaces.
- Added an entity messaging-agent generator that converts matching `EntityEvent` instances into
  BABj agent broadcasts.
- Added administration specialization for `AbstractSettings` entities using
  `AbstractSettingsDTO/Home`, `GenericSettingsView`, and `GenericSettingsWindow`.
- Added a complete manual test matrix in `docs/TESTING.md` and raised the plugin version to
  **1.3.0**.

### Added — extended BABj tooling

- Added a visual CRUD Designer with field selection, ordering, Vaadin editor overrides, and live
  grid/form previews.
- Added a type-safe REST endpoint generator for entities implementing
  `RestPublicIdEntityInterface`; generated Home services implement the matching BABj REST contract.
- Added CSV and Excel import-window scaffolds, report-window scaffolds, and optional View export.
- Added **BABj Agent Studio**, a static event → agent → safety criterion → action topology browser
  with event-reaction simulation and source navigation.
- Raised the plugin version to **1.2.0**.

### Added — BABj navigation and completion

- Added gutter navigation between related Entity, DTO, Home, View, and EditWindow classes.
- Added a visual **BABj Navigator** tool window that shows the complete module chain and missing
  artifacts.
- Added context-aware completion for BABj annotation property names and full `@ColumnNames`
  definitions.
- Added an inspection for invalid property references in filter, visibility, status, uniqueness,
  and Vaadin binding annotations.
- The CRUD dialog now offers every concrete `OperaterEntityInterface` implementation and discovers
  role constants from concrete `AbstractRoles` subclasses, including inherited constants, with
  multi-role selection for `@AdminTypes`.
- Added Borivoj Bogdanović as the plugin author and raised the plugin version to **1.1.0**.

### Changed

- Standardized all user-facing plugin messages, inspection descriptions, live-template descriptions,
  and project documentation in English.
- Added a new BABj visual identity with light and dark plugin icons and a dedicated CRUD action icon.
- Raised the plugin version to **1.0.4**.
- Built against IntelliJ IDEA Community **2024.3**, with compatibility declared through build `299.*`.

### Added — advanced entity scanning

- Association display properties are discovered on the target entity (`naziv` → `username` → the
  first `String` field) instead of always using `naziv`.
- The operator (`K`) type is detected through an `OperaterEntityInterface` implementation.

### Added — field validation

- Inspection `BABjHomeSelect` validates `alias.property` references in `getSelect()` against entity
  fields and resolves aliases from `getJoin()`, including chained joins.
- Inspection `BABjColumnNames` validates `@ColumnNames` entity paths and service DTO keys.

### Added

- **CRUD generator** (`Alt+Insert` → *BABj CRUD*) creates `DTO`, `Home`, `View`, and `EditWindow`
  artifacts from a JPA entity, maps associations and enums to the corresponding UI controls, and
  never overwrites existing files.
- **Live templates**: `bview`, `bwin`, `bhome`, and `bdto`.
- **Inspection** `BABjMissingEditWindow` reports a `GenericView` without its matching
  `Edit<Entity>Window` and provides a generation quick fix.
