# BABj — IntelliJ IDEA Plugin

[![Version](https://img.shields.io/badge/version-1.8.0-blue)](gradle.properties)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2024.3%2B-purple)](https://www.jetbrains.com/idea/)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)

IntelliJ IDEA development support for the
[BAB library](https://github.com/bora5/BAB) (`rs.co.bora5.programs.bab`). The plugin understands
BABj project conventions and helps developers generate, inspect, navigate, and understand BABj
application code without repeating framework boilerplate by hand.

**Author:** Borivoj Bogdanović

## Highlights

| Feature | What it provides |
|---|---|
| **Code Generator** | Generates a complete DTO, Home, View, and EditWindow quartet from a JPA entity |
| **CRUD Designer** | Selects and reorders fields, chooses Vaadin editors, and previews the generated grid and form |
| **Extended Generators** | Scaffolds REST endpoints, imports, reports, exports, attachments, and messaging agents |
| **Inspections** | Detects invalid BABj mappings and offers safe migrations to reusable BAB helper APIs |
| **Smart Completion** | Completes entity properties and full column definitions inside BABj annotations |
| **BABj Navigator** | Connects related Entity, DTO, Home, View, and EditWindow classes |
| **Lifecycle Navigator** | Visualizes the lifecycle of events implemented by a concrete BABj class |
| **Agent Studio** | Shows static event routing, safety criteria, and actions for BABj agents |
| **Live Templates** | Inserts individual `bview`, `bwin`, `bhome`, and `bdto` class skeletons |

## Quick start

1. Open a project that uses BAB and wait for IntelliJ IDEA to finish indexing.
2. Place the caret inside a JPA entity annotated with `@Entity` or inheriting `AbstractEntity`.
3. Press `Alt+Insert` and select **BABj Code Generator**.
4. Review the detected packages, operator type, roles, artifacts, and CRUD fields.
5. Select **Generate** and inspect the created Java sources.

The generator is also available from **Find Action** and the Java editor context menu. Existing
files are preserved by default. Recreating them must be explicitly enabled and confirmed in a
second warning dialog.

## Code generation

### CRUD quartet

The main generator creates the four conventional BABj artifacts:

| Artifact | Conventional package | Generated content |
|---|---|---|
| `<Entity>DTO` | `…front.views.projections` | Projection constructor, fields, getters, and setters |
| `<Entity>Home` | `…sesion` | BABj service with `getSelect()` and the required `getJoin()` clauses |
| `<Entity>View` | `…front.views` | Declarative `GenericView` with roles, columns, route, and enabled actions |
| `Edit<Entity>Window` | `…front.windowses` | `GenericWindow` with `@PropertyId` fields and matching Vaadin controls |

The **Artifacts** tab configures class names, packages, operator type, roles, route, and page title.
The plugin scans the project for concrete `OperaterEntityInterface` implementations and concrete
subclasses of `AbstractRoles`. Role discovery includes inherited and locally declared
`public static final String` constants and supports multiple selections for `@AdminTypes`.

### Field mapping

| Entity field | Generated DTO/Home representation | EditWindow control |
|---|---|---|
| `String` | `String` | `TextField` |
| `boolean` / `Boolean` | same type | `Checkbox` |
| integral and floating-point numbers | same type | matching numeric field |
| `BigDecimal` | `BigDecimal` | `BigDecimalField` |
| `LocalDate` | `LocalDate` | `DatePicker` |
| `LocalDateTime` | `LocalDateTime` | `DateTimePicker` |
| `LocalTime` | `LocalTime` | `TimePicker` |
| enum | concrete enum type | typed `ComboBox` populated with `values()` |
| `@ManyToOne` / `@OneToOne` | associated display value as `String` | typed BABj combo box |
| `@OneToMany` / `@ManyToMany` | skipped | not generated |

For entity associations, the display property is selected from a target entity's String fields in
this order: `naziv`, `username`, `name`, `oznaka`, then the first remaining String field. The Home
projection and joins, DTO field, View column, and EditWindow binding are generated from the same
field model.

The **CRUD Designer** tab can include, exclude, and reorder fields, override the inferred editor,
and preview the resulting `@ColumnNames` declaration and edit form before files are written.

### Additional generators

The **Additional generators** tab can also create:

- a REST endpoint extending `AbstractEndpoint` for entities implementing
  `RestPublicIdEntityInterface`;
- CSV or Excel import-window scaffolds using BABj upload base classes;
- a `GenericReportWindow` scaffold with report lifecycle hooks;
- `@EnableExport` support on a generated View;
- attachment row actions for compatible database or file-system attachment entities.

The separate **Agent** tab generates a CDI messaging agent that converts matching entity lifecycle
events into messages for other BABj agents.

## Inspections and completion

The plugin registers a dedicated **BABj** inspection group under
**Settings → Editor → Inspections → Java → BABj**.

- **Missing Edit window** finds a `GenericView` without the conventional
  `Edit<Entity>Window` and provides an `Alt+Enter` quick fix.
- **Home select validation** checks `getSelect()` property paths and resolves aliases declared in
  `getJoin()`, including chained joins.
- **ColumnNames validation** verifies entity paths and DTO keys. It accepts optional filter and
  sorting flags in `property~key~Label~filterEnabled~sortingEnabled` entries.
- **Annotation property validation** checks references used by filters, visibility, status,
  uniqueness, SQL-field, and `@PropertyId` annotations.
- **Legacy ComboBox simplification** recognizes hand-written lazy `DataProvider` refresh methods
  and provides an `Alt+Enter` quick fix that replaces them with `createSimpleComboBox(...)` or
  `createDependentComboBox(...)`. The dependent conversion removes a redundant refresh listener
  only when it contains no additional business logic. Refresh calls and their unique `setLabel(...)`
  calls may be grouped separately within the same method. It also recognizes one or more legacy
  admin-only `ComboBox` plus-button wrappers in the same block and replaces them with
  `comboWithAddButton(...)`, removing obsolete fields, conditionals, and imports.
- **Legacy main-menu migration** recognizes the standard `FavTab`, `setVisible(...)`, and role
  switch pattern in `GenericMainView` subclasses. Its quick fix creates a declarative
  `MenuDefinition`, preserves section and item order, combines roles, and removes duplicate tab
  additions. BAB then derives the visible menu, allowed routes, empty sections, and safe favourites
  from that single definition.
- **Common BAB simplifications** recognizes five BAB 1.7 patterns and offers focused quick fixes:
  strict `resultOrNull(...)` / `resultOrDefault(...)` JPA results, `handleUiTask(...)` asynchronous
  callbacks, semantic `NotificationFactory` and `ConfirmDialogs` calls, enum ComboBox factories,
  and shared `BabDateFormats` constants. Patterns with logging, additional callbacks, ambiguous
  labels, or other custom behavior are intentionally left unchanged.

Code completion inside supported BABj annotation strings suggests entity properties and one-level
association paths. In `@ColumnNames`, it can insert a complete `property~key~Label` entry.

## Navigation tools

### BABj Navigator

Related Entity, DTO, Home, View, and EditWindow classes receive a BABj gutter icon. Click it to
open another artifact from the same module. For an overview of the entire quartet, open
**View → Tool Windows → BABj Navigator** and double-click any discovered class. Missing artifacts
are displayed as part of the module chain.

### BABj Lifecycle

Open a concrete BABj service, window, or report class, then open
**View → Tool Windows → BABj Lifecycle**. The tool shows the event flows available for that class,
including framework steps, conditions, side effects, inherited hooks, and overridden methods.

Only events backed by a non-empty lifecycle hook are offered. Double-click a diagram node to jump
to its implementation. Use **Copy image** to place the complete diagram on the clipboard or
**Export PNG** to save it as a full-size image, including portions outside the current scroll
viewport.

### BABj Agent Studio

Open **View → Tool Windows → BABj Agent Studio** and select **Scan project**. Agent Studio finds
project implementations of `Agent`, groups their event, `SafetyCriterion`, and `AgentAction` types,
and shows which agents can react to a selected event. Double-click a source-backed node to open it.

Agent Studio performs static analysis only: it does not instantiate agents or execute application
code.

## Installation

### Build and install from disk

```powershell
.\gradlew.bat clean test buildPlugin verifyPlugin
```

The installable ZIP is created in `build/distributions/`. Install it using:

**Settings → Plugins → ⚙ → Install Plugin from Disk**

Restart IntelliJ IDEA when replacing an older version of the plugin.

### Run in a development IDE

```powershell
.\gradlew.bat runIde
```

On Linux or macOS, use `./gradlew` instead of `.\gradlew.bat`.

## Requirements

- IntelliJ IDEA Community or Ultimate 2024.3 or newer (build 243+)
- JDK 21 for building the plugin
- a project with the BAB library available as a dependency

## Testing

See [docs/TESTING.md](docs/TESTING.md) for the complete manual test matrix covering generation,
inspections, completion, navigation, lifecycle diagrams, and Agent Studio.

## Project structure

```text
src/main/java/rs/co/bora5/plugins/babj/
├── action/        generator action, dialog, and CRUD Designer
├── agent/         Agent Studio topology browser and static simulation
├── completion/    context-aware BABj annotation completion
├── gen/           source templates and file generation
├── inspection/    BABj inspections and quick fixes
├── lifecycle/     lifecycle resolution and interactive diagrams
├── model/         entity parsing, field mapping, naming, and generation context
└── navigation/    related-artifact resolution, gutter links, and Navigator tool window
src/main/resources/
├── META-INF/      plugin metadata and Marketplace icons
├── icons/         action and tool-window icons
├── inspectionDescriptions/
└── liveTemplates/BABj.xml
```

## Roadmap

- XSD/JAXB configuration wizard for `GenericXMLUploadFileWindow`
- specialized mail/SMS server and outbox administration wizards
- runtime Agent Studio tracing in addition to the current static analysis
- richer lifecycle diagrams as BAB gains new framework events

## License

Copyright 2026 Borivoj Bogdanović.

Licensed under the [Apache License 2.0](LICENSE).
