# BABj IntelliJ Plugin — Manual Test Matrix

This guide covers every plugin feature. Use a disposable branch of a BABj application because the
generator creates real Java source files. It skips existing files unless recreation is explicitly
enabled and confirmed in a second warning dialog.

## 1. Install the development build

Build the plugin:

```powershell
.\gradlew.bat buildPlugin
```

Install `build/distributions/BABjInteliJ-1.6.0.zip` through:

`Settings → Plugins → ⚙ → Install Plugin from Disk`

Restart the IDE when replacing an older plugin build. Alternatively, run `.\gradlew.bat runIde` to
open an isolated IntelliJ sandbox.

Make sure BAB is a project dependency and wait for indexing to finish. Enable all inspections under
`Settings → Editor → Inspections → Java → BABj`.

## 2. Features already smoke-tested

These are the checks already confirmed in WasteX and only need repeating after a regression:

- `Alt+Insert → BABj Code Generator` is available in an entity.
- The CRUD quartet is generated.
- completion works in BABj annotation strings.
- `getSelect()` and `@ColumnNames` validation works.
- gutter navigation opens the related Entity, DTO, Home, View, and EditWindow.

## 3. CRUD Designer

1. Open a JPA entity with at least a `String`, number, boolean, date, enum, and `@ManyToOne`
   association whose type inherits BAB `AbstractEntity`. Place `@ManyToOne` on its getter.
2. Run `Alt+Insert → BABj Code Generator` and open **CRUD Designer**.
3. Clear one field, move another field to the top, and override one editor type.
4. Confirm that **Grid projection** and **Edit form** previews update immediately.
5. Generate into new class names/packages.

Expected:

- the cleared field is absent from DTO, Home projection, View columns, and EditWindow;
- the reordered field order is identical in DTO constructor, `getSelect()`, and `@ColumnNames`;
- the selected editor is used in the generated EditWindow;
- associations still produce a join and typed combo box.
- association fields are generated as their display `String` in DTO and Home;
- the association display field is selected in this order: `naziv`, `username`, `name`, `oznaka`,
  then any remaining `String` field;
- enum fields retain their concrete type and `x.<field>` Home projection, with a typed combo box
  populated with `<Enum>.values()` in EditWindow.

## 4. Operator and role discovery

1. Ensure the project has two concrete `OperaterEntityInterface` implementations.
2. Ensure a concrete `AbstractRoles` subclass inherits constants and declares local
   `public static final String` constants.
3. Open the generator.

Expected:

- both operator types are offered and the field remains editable;
- inherited and local role constants are listed;
- multiple selected roles produce an `@AdminTypes(roles = {...})` array;
- no `ADMIN` or `Roles` class name is hard-coded.

## 5. Safe file recreation

1. Generate any artifact.
2. Add a recognizable comment to it.
3. Run the same generation again.
4. Confirm that the file is skipped while **Recreate existing files** is disabled.
5. Enable **Recreate existing files**, start generation, and cancel the warning dialog.
6. Confirm that the recognizable comment remains.
7. Repeat, enable recreation, and approve the warning.

Expected: the safe run lists the file under **Skipped (already exists)**. Cancelling the warning
changes nothing. Approving it replaces the file and lists it under **Recreated**.

## 6. REST endpoint

Positive case:

1. Use an entity implementing `RestPublicIdEntityInterface`.
2. Select DTO, Home, and **REST endpoint**; enter a path such as `/test-items`.
3. Generate.

Expected:

- `<Entity>Endpoint` extends `AbstractEndpoint<Entity, EntityHome, EntityDTO, Operator>`;
- it has the selected `@Path`;
- the generated Home implements `RestPublicIdHomeInterface<Entity>`;
- generated sources compile in the application.

Negative case: use an ordinary entity. The REST checkbox must be disabled. If a pre-existing Home
does not implement the REST Home contract, validation must explain the required change instead of
generating a broken endpoint.

## 7. Import and export

1. Generate a new View together with either **CSV import window** or **Excel import window**.
2. Confirm that selecting CSV clears Excel and vice versa.
3. Also select **Enable View export**.

Expected:

- the View has `@EnableImport` and `@EnableExport`;
- it contains one injected field annotated with `@ImportWindow`;
- CSV generates `Import<Entity>CsvWindow`; Excel generates `Import<Entity>XlsWindow`;
- the import class extends the matching BABj upload base class and exposes a clear `doWork(...)`
  business hook.

Runtime check: implement the generated `doWork(...)` TODO, start the application, and confirm that
the View toolbar opens exactly one import dialog. Import a small file and verify persistence. The
Export button should download the visible grid data.

## 8. Report scaffold

1. Select **Report window** and generate.
2. Compile the application.

Expected: `<Entity>ReportWindow` extends `GenericReportWindow<Entity, Operator>`, calls
`super(Entity.class)`, and contains `createContent(...)` and `doMain()` hooks. Add one bound report
parameter and a minimal `doMain()` implementation, then open the window from the application and
verify the BABj validation/lifecycle flow.

## 9. Attachments

Prepare an entity that already implements either:

- `MultiAttachmentEntityInterface<Parent, Attachment>`, or
- `MultiFileSystemAttachmentEntityInterface<Parent, Attachment, Operator>`.

The attachment type must be an `AbstractEntity` implementing the matching attachment interface.

1. Open the parent entity and run the generator.
2. Confirm that **Attachment row action** displays the resolved attachment type.
3. Generate a new View with this option enabled.

Expected:

- incompatible entities have the option disabled;
- the View implements the correct database/file-system BABj View interface;
- `generateComponents(...)` contains the correctly typed BABj multi-file input;
- compilation succeeds without raw generic types.

Runtime check: log in with one of the View's admin roles, select a row, and open
**Pregled priloga**. Upload and download a small file. For file-system attachments also verify that
the file is written below the active settings `filesAttachmentLocation`.

## 10. Entity messaging agent

1. Select **Entity messaging agent** for an entity and generate.
2. Open **View → Tool Windows → BABj Agent Studio** and press **Scan project**.
3. Select `EntityEvent` and press **Simulate event**.

Expected:

- `<Entity>MessagingAgent` is an `@ApplicationScoped` `AbstractAgent`;
- Agent Studio shows it as reacting to `EntityEvent`;
- the generated handler broadcasts an `EntityLifecycleMessage` containing event type and entity id.
- repeated scans complete without EDT/read-action exceptions and do not freeze the IDE UI.

Runtime check: register a small receiver agent supporting `AgentMessageEvent`, fire
`new EntityEvent("UPDATE", entity)` through `AgentContext`/the runtime manager, and verify that the
receiver gets the broadcast. The generated agent intentionally does not send e-mail/SMS directly;
receivers decide how the message is delivered.

## 11. Agent Studio topology

Create or use agents that reference concrete `AgentEvent`, `SafetyCriterion`, and `AgentAction`
types. Open **BABj Agent Studio** and scan.

Expected:

- every concrete project Agent is a root child;
- Events, Safety criteria, and Actions are grouped below it;
- selecting an event marks matching agents with `✓ reacts`;
- double-clicking any source-backed node opens its Java class;
- during indexing the tool window shows a clear "available after indexing" status.

This is a static simulation. It never instantiates agents or executes application code.

## 12. Remaining inspections and quick fixes

### Missing EditWindow

Create a `GenericView` without `Edit<Entity>Window`. Expected: `BABjMissingEditWindow` reports it;
`Alt+Enter → Generate Edit<Entity>Window` creates the missing file.

### Annotation property references

Insert a nonexistent property into annotations such as `@AddCondition`, `@EnabledForStatus`,
`@AdminVisibleFields`, `@SqlFieldName`, `@PropertyId`, or `@SingleUniqueField`. Expected: the
property is highlighted and completion offers valid entity paths.

### Optional `@ColumnNames` flags

Use `property~key~Label~true~false` for a valid entity/DTO property. Expected: the fourth
`filterEnabled` and fifth `sortingEnabled` tokens are accepted. Replace either flag with a value
other than `true` or `false`; the inspection should report the invalid boolean.

### Home aliases

In `getSelect()`, reference an unknown alias or an invalid joined property. In `getJoin()`, add a
valid chained join and use it from `getSelect()`. Expected: invalid references are reported while
valid chained aliases are accepted.

### Legacy ComboBox simplification

Create a private refresh method that initializes a `ComboBox` with
`DataProvider.fromFilteringCallbacks(...)`, `findAllLazy(...)`, and `findSizeLazy(...)`. Call it
next to `setLabel(...)` in `createContent(...)`. Expected: the inspection offers
`Alt+Enter → Replace with createSimpleComboBox()` and removes the refresh method and unused
`DataProvider` import.

Repeat with `findAllLazyWithOtherEntity(...)` / `findSizeLazyWithOtherEntity(...)` and the standard
parent listener that only calls `getLazyDataView().refreshAll()` and `setValue(null)`. Expected:
the fix generates `createDependentComboBox(...)` and removes that redundant listener. Add any
third statement to the listener and confirm that no automatic fix is offered, preserving custom
business behavior.

Create the legacy admin-only add-button pattern with `Button`, `HorizontalLayout`, the standard
plus icon, and a two-step `editWindow.init(...); editWindow.open();` click listener. Add both the
wrapper and plain ComboBox through a later `if (getAdmin()) ... else ...` block. Expected:
`Alt+Enter → Replace admin ComboBox wrappers with comboWithAddButton()` creates a local wrapper,
chains the `init(...).open()` callback, removes the conditionals, obsolete fields, and unused
imports. Repeat with two or more wrappers inside the same admin block; all are converted together.
Add custom click-listener behavior and confirm that the fix is not offered.

## 13. Live templates

In a Java class, type each abbreviation and press `Tab`:

- `bdto`
- `bhome`
- `bview`
- `bwin`

Expected: the matching BABj class skeleton is inserted and template variables are editable.

## 14. Navigator tool window and action contexts

1. Open **View → Tool Windows → BABj Navigator** from each member of a quartet.
2. Press **Refresh from editor** and double-click every found artifact.
3. Open Find Action and the editor context menu, not only `Alt+Insert`.

Expected: the module graph shows found and missing artifacts, navigation opens source, and the code
generator remains available in all supported action contexts while a Java editor is active.
Repeated refreshes must complete without EDT/read-action exceptions or blocking the IDE UI.

## 15. Lifecycle image copy and export

1. Open a concrete BABj class with a non-empty supported lifecycle hook.
2. Open **View → Tool Windows → BABj Lifecycle** and select an event.
3. Click **Copy image** and paste into an image editor, document, or chat application.
4. Click **Export PNG**, choose a location, and open the saved file.

Expected: both outputs contain the complete diagram at its natural size, even when the tool window
shows scrollbars. The buttons are disabled when no lifecycle is available. The suggested filename
contains the class and event names and ends in `.png`.

## 16. Release verification

Before publishing a plugin build, run:

```powershell
.\gradlew.bat clean test buildPlugin verifyPlugin
```

Expected: compilation and packaging succeed and Plugin Verifier reports compatibility for all
configured IntelliJ versions. The known `untilBuild = 299.*` structure warning is non-fatal.
