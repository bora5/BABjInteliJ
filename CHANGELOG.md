<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# BABj Support Changelog

## [Unreleased]

### Changed

- Build cilja IntelliJ IDEA **Ultimate 2026.2** (build 262).

### Added (napredno skeniranje)

- Display polje asocijacije se skenira sa ciljanog entiteta (`naziv` → `username` →
  prvo `String` polje) umesto fiksnog `naziv`.
- Operater (`K`) tip se auto-detektuje preko implementacije `OperaterEntityInterface`.

### Added

- **CRUD generator** (`Alt+Insert` → *babj CRUD*): iz JPA entiteta generiše
  `DTO`, `Home`, `View` i `EditWindow` u odgovarajućim paketima, sa mapiranjem
  asocijacija (`@ManyToOne`/`@OneToOne` → join + combo), enum-a (`ComboBox` iz
  `values()`) i osnovnih tipova na Vaadin polja. Konfiguracioni dijalog i
  zaštita od gaženja postojećih fajlova.
- **Live templates**: `bview`, `bwin`, `bhome`, `bdto`.
- **Inspekcija** `BabjMissingEditWindow`: upozorava kad `GenericView` nema
  prateći `Edit<Entity>Window`, sa quick-fix-om koji ga generiše.
