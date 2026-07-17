# BABj Support — IntelliJ plugin

Development support for the **BABj** library (`rs.co.bora5.programs.bab`). The plugin automates
BABj conventions and reduces repetitive boilerplate.

**Author:** Borivoj Bogdanović

## Features

### 1. CRUD quartet generator

Starting from a JPA entity (`@Entity` or `AbstractEntity`), the generator creates four files in the
conventional packages used by BABj projects:

| Artifact | Package | Contents |
|---|---|---|
| `<Entity>DTO` | `…front.views.projections` | Projection with a `(Long id, …)` constructor, getters, and setters; associations are flattened to `String` |
| `<Entity>Home` | `…sesion` | `@Stateless @LocalBean @Primary` service with `getSelect()` and `getJoin()` |
| `<Entity>View` | `…front.views` | Declarative `GenericView` with `@EnableNew/Edit/Delete`, `@AdminTypes`, `@ColumnNames`, and `@Route` |
| `Edit<Entity>Window` | `…front.windowses` | `GenericWindow` with `@PropertyId` fields and combo-box factory calls |

The generator maps entity fields as follows:

- `@ManyToOne` / `@OneToOne` → `LEFT JOIN`, an `alias.<display>` projection, and
  `createSimpleComboBox(...)` in the edit window. The display property is discovered on the target
  entity, preferring `naziv`, then `username`, then the first `String` field.
- `enum` → a `ComboBox` populated from `values()`.
- `LocalDate`, `LocalDateTime`, `LocalTime`, numbers, `boolean`, and `String` → matching Vaadin fields.
- Collections (`@OneToMany` / `@ManyToMany`) are skipped.

The operator (`K`) type is detected by scanning the project for concrete implementations of
`OperaterEntityInterface` and offered as an editable choice. The dialog also discovers concrete
subclasses of `AbstractRoles`, collects all inherited and locally declared `public static final
String` constants, and supports selecting multiple roles for `@AdminTypes`.

**Run it:** place the caret in an entity, press `Alt+Insert` (Generate), and select
**BABj Code Generator**. The dialog pre-fills the base package, operator choices,
roles registry, role choices, view class name, route, and title, and lets you choose which artifacts
to generate. Existing files are never overwritten.

The **CRUD Designer** tab lets you include/exclude and reorder fields, override the inferred Vaadin
editor, and preview the resulting `@ColumnNames` projection and edit form. The same field model is
used by every generated CRUD artifact.

### 2. Extended generators

- A REST endpoint extending `AbstractEndpoint`, enabled only when the selected entity implements
  `RestPublicIdEntityInterface`; its generated Home receives `RestPublicIdHomeInterface`.
- CSV and Excel import-window scaffolds with BABj's upload base classes.
- A `GenericReportWindow` scaffold with lifecycle hooks for print, e-mail, or Excel output.
- Optional `@EnableExport` support on generated views.

### 3. Live templates

`bview`, `bwin`, `bhome`, and `bdto` provide quick skeletons for individual BABj classes.

### 4. Inspections

- **Missing Edit window** reports a `GenericView` without its conventional
  `Edit<Entity>Window` and provides an `Alt+Enter` quick fix to generate it.
- **`getSelect()` field validation** checks every `alias.property` token against the entity model.
  Alias `x` is the entity; additional aliases are resolved from `getJoin()`, including chained joins.
  Functions such as `CONCAT(...)` and literals are ignored.
- **`@ColumnNames` validation** checks each entity property path and verifies that the optional key
  is available from the service DTO.
- **Annotation property validation** checks entity-property references in filters, field-visibility,
  status, uniqueness, and `@PropertyId` bindings.

### 5. BABj Navigator

Recognized Entity, DTO, Home, View, and EditWindow classes have a BABj gutter icon. Clicking the
icon lists every artifact in the same module and opens the selected class. The **BABj Navigator**
tool window shows the complete module chain, including missing artifacts, and supports double-click
navigation.

### 6. Smart annotation completion

Code completion inside BABj annotation strings suggests valid entity properties and one-level
association paths. `@ColumnNames` completion inserts complete `property~key~Label` definitions.
Completion is also available for `@AddCondition`, `@AdminVisibleFields`, `@EnabledForStatus`,
`@SqlFieldName`, `@PropertyId`, and `@SingleUniqueField` property attributes.

### 7. BABj Agent Studio

The **BABj Agent Studio** tool window scans project implementations of `Agent`, extracts event types
referenced by `supports(...)`, and shows statically referenced `SafetyCriterion` and `AgentAction`
types. Select an event to see which agents can react, and double-click a node to open its source.
This is a static simulation: it does not instantiate agents or execute application code.

## Roadmap

- attachment module assistance based on BABj attachment entity/view interfaces
- messaging scaffolds for e-mail, SMS, and agent messages
- administration-specific generators and inspections
- runtime Agent Studio tracing in addition to the current static simulator

## Build and run

```bash
./gradlew runIde          # start a sandbox IntelliJ instance with the plugin
./gradlew buildPlugin     # create the ZIP in build/distributions/
./gradlew verifyPlugin    # run compatibility verification
```

Install the ZIP from `build/distributions/` using
*Settings → Plugins → ⚙ → Install Plugin from Disk*.

## Requirements

- Built against IntelliJ IDEA Community **2024.3** on JDK 21.
- Compatible with IntelliJ builds **243** through **299.***, including current Ultimate versions.
- JDK 21 for local builds.

## Project structure

```text
src/main/java/rs/co/bora5/plugins/babj/
├── action/        GenerateBABjCrudAction and configuration dialog
├── agent/         Agent Studio topology browser and static simulator
├── completion/    context-aware BABj annotation completion
├── gen/           CodeTemplates renderer and file generator
├── inspection/    BABj inspections and quick fixes
├── model/         entity parser, field model, naming, and generation context
└── navigation/    related-artifact resolver, gutter links, and Navigator tool window
src/main/resources/
├── META-INF/      plugin.xml and plugin icons
├── icons/         action icons
├── inspectionDescriptions/
└── liveTemplates/BABj.xml
```
