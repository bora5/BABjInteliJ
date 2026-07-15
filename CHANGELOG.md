<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# BABj Support Changelog

## [Unreleased]

### Added

- **CRUD generator** (`Alt+Insert` → *babj CRUD*): iz JPA entiteta generiše
  `DTO`, `Home`, `View` i `EditWindow` u odgovarajućim paketima, sa mapiranjem
  asocijacija (`@ManyToOne`/`@OneToOne` → join + combo), enum-a (`ComboBox` iz
  `values()`) i osnovnih tipova na Vaadin polja. Konfiguracioni dijalog i
  zaštita od gaženja postojećih fajlova.
- **Live templates**: `bview`, `bwin`, `bhome`, `bdto`.
- **Inspekcija** `BabjMissingEditWindow`: upozorava kad `GenericView` nema
  prateći `Edit<Entity>Window`, sa quick-fix-om koji ga generiše.
