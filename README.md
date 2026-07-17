# BABj Support — IntelliJ plugin

Razvojna podrška za **BABj** biblioteku (`rs.co.bora5.programs.bab`). Automatizuje
konvencije koje BABj nameće i skraćuje pisanje boilerplate-a.

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
- `@ManyToOne` / `@OneToOne` → `LEFT JOIN`, projekcija `alias.<display>`, `createSimpleComboBox(...)` u prozoru.
  Display polje se **skenira sa ciljanog entiteta** (prednost `naziv`, pa `username`, pa prvo `String` polje);
- `enum` → `ComboBox` napunjen iz `values()`;
- `LocalDate/LocalDateTime/LocalTime`, brojevi, `boolean`, `String` → odgovarajuće Vaadin polje;
- kolekcije (`@OneToMany`/`@ManyToMany`) se preskaču.

Operater (`K`) tip se auto-detektuje skeniranjem projekta za implementaciju
`OperaterEntityInterface` (npr. `Korisnik`) i prefiluje u dijalogu.

**Pokretanje:** kursor u entitetu → `Alt+Insert` (Generate) → **BABj CRUD (DTO, Home, View, Window)**.
Otvori se dijalog sa unapred popunjenim parametrima (osnovni paket, `K` tip, rola,
naziv view-a, ruta, naslov) i čekboksovima koji artefakti se generišu. Postojeći fajlovi
se **ne** gaze.

### 2. Live templates

`bview`, `bwin`, `bhome`, `bdto` — brzi skeleti za pojedinačne klase.

### 3. Inspekcije

- **Nedostajući Edit prozor** — upozorava kad `GenericView` nema prateći
  `Edit<Entity>Window` (obavezno po konvenciji), sa quick-fix-om (`Alt+Enter`) koji ga generiše.
- **Provera polja u `getSelect()`** — svaki `alias.polje` token u projekciji Home klase se
  validira protiv stvarnih polja entiteta, uz razrešavanje alias-a iz `getJoin()`
  (`x` = entitet; ostali alias-i iz `LEFT JOIN`-ova, uključujući ulančane join-ove).
  Funkcije (`CONCAT(...)`) i literali se preskaču.
- **Provera `@ColumnNames`** — putanja kolone (prvi `~`-token, uz `*` prefiks) se validira
  protiv polja entiteta, a ključ (drugi `~`-token) protiv **DTO-a servisa** — tj. da je kolona
  zaista „podržana iz servisa".

## Pokretanje i build

```bash
./gradlew runIde          # diže sandbox IntelliJ sa plugin-om
./gradlew buildPlugin     # pravi .zip u build/distributions/
./gradlew verifyPlugin    # provera kompatibilnosti
```

Distribucija: `.zip` iz `build/distributions/` se instalira preko
*Settings → Plugins → ⚙ → Install Plugin from Disk*.

## Zahtevi

- Build cilja IntelliJ IDEA **Community 2024.3** (stabilno izdanje, gradi se na JDK 21).
- Učitava se od build-a **243** naviše, bez gornje granice — koristi samo stabilne Java PSI API-je,
  pa se instalira i radi i u aktuelnom **2026.2 / Ultimate**-u.
- Build na **JDK 21**.

## Struktura

```
src/main/java/rs/co/bora5/plugins/babj/
├── action/        GenerateBABjCrudAction + dijalog
├── gen/           CodeTemplates (renderer) + writer/generator
├── inspection/    MissingEditWindowInspection + quick-fix
└── model/         parser entiteta (EntityModel), polja, naming, kontekst
src/main/resources/
├── META-INF/plugin.xml
└── liveTemplates/BABj.xml
```
