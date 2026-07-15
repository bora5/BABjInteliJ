# BABj Support — IntelliJ plugin

Razvojna podrška za **babj** biblioteku (`rs.co.bora5.programs.bab`). Automatizuje
konvencije koje babj nameće i skraćuje pisanje boilerplate-a.

## Šta radi

### 1. Generator kvarteta (glavna funkcija)

Iz JPA entiteta (`@Entity` / `AbstractEntity`) napravi četiri fajla u odgovarajućim
paketima, po istim konvencijama koje se vide u `wastex` kodu:

| Artefakt | Paket | Šta sadrži |
|---|---|---|
| `<Entity>DTO` | `…front.views.projections` | Projekcija sa konstruktorom `(Long id, …)` + geteri/seteri; asocijacije se ravnaju u `String` |
| `<Entity>Home` | `…sesion` | `@Stateless @LocalBean @Primary` servis sa `getSelect()` / `getJoin()` |
| `<Entity>View` | `…front.views` | Deklarativni `GenericView` (`@EnableNew/Edit/Delete`, `@AdminTypes`, `@ColumnNames`, `@Route`) |
| `Edit<Entity>Window` | `…front.windowses` | `GenericWindow` sa `@PropertyId` poljima i combo-box factory pozivima |

Generator prepoznaje tipove polja i mapira ih:
- `@ManyToOne` / `@OneToOne` → `LEFT JOIN`, projekcija `alias.naziv`, `createSimpleComboBox(...)` u prozoru;
- `enum` → `ComboBox` napunjen iz `values()`;
- `LocalDate/LocalDateTime/LocalTime`, brojevi, `boolean`, `String` → odgovarajuće Vaadin polje;
- kolekcije (`@OneToMany`/`@ManyToMany`) se preskaču.

**Pokretanje:** kursor u entitetu → `Alt+Insert` (Generate) → **babj CRUD (DTO, Home, View, Window)**.
Otvori se dijalog sa unapred popunjenim parametrima (osnovni paket, `K` tip, rola,
naziv view-a, ruta, naslov) i čekboksovima koji artefakti se generišu. Postojeći fajlovi
se **ne** gaze.

### 2. Live templates

`bview`, `bwin`, `bhome`, `bdto` — brzi skeleti za pojedinačne klase.

### 3. Inspekcija

Upozorava kad `GenericView` nema prateći `Edit<Entity>Window` (obavezno po konvenciji),
sa quick-fix-om (`Alt+Enter`) koji ga generiše.

## Pokretanje i build

```bash
./gradlew runIde          # diže sandbox IntelliJ sa plugin-om
./gradlew buildPlugin     # pravi .zip u build/distributions/
./gradlew verifyPlugin    # provera kompatibilnosti
```

Distribucija: `.zip` iz `build/distributions/` se instalira preko
*Settings → Plugins → ⚙ → Install Plugin from Disk*.

## Zahtevi

- Cilja IntelliJ **2024.3+** (Community Edition je dovoljan — koristi se samo Java PSI).
- Build na JDK **21**.

## Struktura

```
src/main/java/rs/co/bora5/plugins/babj/
├── action/        GenerateBabjCrudAction + dijalog
├── gen/           CodeTemplates (renderer) + writer/generator
├── inspection/    MissingEditWindowInspection + quick-fix
└── model/         parser entiteta (EntityModel), polja, naming, kontekst
src/main/resources/
├── META-INF/plugin.xml
└── liveTemplates/babj.xml
```
