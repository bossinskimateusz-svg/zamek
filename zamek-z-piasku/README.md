# ZamekZPiasku

Plugin pod Paper/Spigot 1.21.x realizujący event **"Zamek z Piasku"**:

- co jakiś czas (albo na komendę admina) na serwerze pojawia się zapowiedź z odliczaniem,
- w wyznaczonym miejscu buduje się piaskowa budowla (ściany, okna, wejście, wieżyczki na rogach,
  dach z kwadratowym świetlikiem, przez który widać niebo — tak jak w oryginalnym evencie),
- na górze ekranu wyświetla się **bossbar** z odliczaniem czasu do końca eventu,
- zabójstwa dokonane **wewnątrz zamku** w trakcie trwania eventu dają graczowi **bonusowe punkty**,
- po zakończeniu event znika, a teren wraca do stanu sprzed budowy.

## Budowanie (kompilacja)

Wymagany JDK 21 oraz Maven. W katalogu projektu:

```bash
mvn clean package
```

Gotowy plik `.jar` znajdzie się w `target/ZamekZPiasku.jar` — wrzuć go do folderu `plugins/`
na serwerze Paper/Spigot 1.21.x i zrestartuj serwer (albo doładuj przez PlugMan).

## Konfiguracja

Wszystko ustawia się w `plugins/ZamekZPiasku/config.yml` (plik generuje się automatycznie
przy pierwszym uruchomieniu — patrz `src/main/resources/config.yml` w projekcie jako wzór):

- `event.auto-start` / `event.interval-minutes` — czy i jak często event ma startować sam,
- `event.duration-seconds` — ile trwa sama walka,
- `event.countdown-before-start` — ile trwa zapowiedź przed wybudowaniem zamku,
- `location.*` — współrzędne, w których ma stanąć zamek,
- `structure.*` — rozmiar budowli, wysokość ścian/wież, rozmiar świetlika, materiały bloków,
- `rewards.bonus-points-per-kill` — ile punktów bonusowych dostaje zabójca,
- `rewards.external-points-command` — **opcjonalnie**: jeśli masz już własny system punktów/rankingu
  (np. z komendą `/points add <gracz> <ilość>`), wpisz tu wzorzec komendy z placeholderami
  `%player%` i `%amount%`, a plugin użyje jej zamiast wbudowanego magazynu punktów,
- `messages.*` / `bossbar.*` — treść komunikatów i wygląd paska bossbar (kolory z `&`).

## Komendy

| Komenda | Opis |
|---|---|
| `/zamek start` | Wymusza start eventu (odliczanie, potem budowa zamku) |
| `/zamek stop` | Natychmiast kończy event i przywraca teren |
| `/zamek build` | Buduje sam podgląd zamku w miejscu gracza (bez liczenia jako event) |
| `/zamek status` | Pokazuje aktualny stan eventu (IDLE / COUNTDOWN / RUNNING) |
| `/zamek reload` | Informacja o przeładowaniu configu |

Wymagane uprawnienie: `zamek.admin` (domyślnie: operatorzy).

## Uwagi

- Bonus liczy się tylko, gdy **zarówno ofiara, jak i zabójca** znajdowali się w chwili zabójstwa
  wewnątrz obszaru zamku — to zapobiega "farmieniu" punktów z bezpiecznej odległości.
- Wygląd budowli jest generowany proceduralnie na podstawie configu (nie jest to gotowy schemat/WorldEdit),
  dzięki czemu możesz dowolnie zmieniać rozmiar i materiały bez edycji kodu.
- Jeśli chcesz, żeby event używał dokładnie Twojego istniejącego systemu punktów/killfeedu/scoreboardu
  (widocznego w Twoim nagraniu), daj znać — to osobny, większy plugin i chętnie go dopiszę.
