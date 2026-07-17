<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# BABj Support Changelog

## [Unreleased]

### Added — BABj navigation and completion

- Added gutter navigation between related Entity, DTO, Home, View, and EditWindow classes.
- Added a visual **BABj Navigator** tool window that shows the complete module chain and missing
  artifacts.
- Added context-aware completion for BABj annotation property names and full `@ColumnNames`
  definitions.
- Added an inspection for invalid property references in filter, visibility, status, uniqueness,
  and Vaadin binding annotations.
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
