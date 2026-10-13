# ChunkCap

Ein Paper-Plugin, das konfigurierbare **Höchstgrenzen pro Chunk für ausgewählte Blocktypen** durchsetzt. Zum Beispiel lassen sich pro Chunk höchstens 16 hopper und 8 spawner erlauben.

## Befehle

- `/chunklimit set <block> <max-per-chunk>` — setzt oder ändert ein Limit (`0` verhindert das Platzieren)
- `/chunklimit remove <block>` — entfernt ein Limit
- `/chunklimit list` — zeigt alle konfigurierten Limits
- `/chunklimit check <block>` — zählt diesen Blocktyp im aktuellen Chunk (nur für Spieler)
- `/chunklimit antilag <true|false>` — schaltet deinen persönlichen, partikelsparenden Umriss ein oder aus
- `/chunklimit antilagcolor <color>` — wählt die Farbe; unterstützt 16 Grundfarben sowie deren `light_`- und `dark_`-Varianten, etwa `light_red`, `blue` oder `dark_purple`
- `/chunklimit theme <theme>` — wählt eines von 15 animierten Umrissthemen, wenn Anti-Lag ausgeschaltet ist: aurora, ocean, sunset, forest, royal, ember, glacier, candy, toxic, galaxy, gold, cherry, pastel, monochrome oder neon
- `/chunklimit reload` — lädt `plugins/ChunkCap/config.yml` neu

Anti-Lag-Modus, Farbe und Thema werden pro Spieler anhand der UUID in `plugins/ChunkCap/player-settings.yml` gespeichert.

## Berechtigungen und LuckPerms

Für die Verwaltungsbefehle gibt es einzelne Berechtigungsknoten:

- `chunkcap.set` — Blocklimits setzen oder ändern
- `chunkcap.remove` — Blocklimits entfernen
- `chunkcap.list` — konfigurierte Limits auflisten
- `chunkcap.check` — Blockanzahl im aktuellen Chunk prüfen
- `chunkcap.reload` — Konfiguration neu laden

Alle fünf Knoten sind standardmäßig für Operatoren aktiv. `chunkcap.admin` gewährt alle fünf Verwaltungsberechtigungen sowie `chunkcap.bypass`. Spieler können ihre eigenen Anti-Lag-Einstellungen ohne Admin-Berechtigung ändern.

Mit LuckPerms kannst du gezielt einzelne Berechtigungen vergeben:

- `/lp group builders permission set chunkcap.set true`
- `/lp user <player> permission set chunkcap.check true`
- `/lp user <player> permission set chunkcap.admin true`

Spieler mit `chunkcap.bypass` können Blöcke trotz erreichtem Limit platzieren; diese Blöcke werden weiterhin mitgezählt. `chunkcap.admin` schließt den Bypass ebenfalls ein. Den Bypass kannst du separat vergeben, zum Beispiel mit `/lp user <player> permission set chunkcap.bypass true`.

Vanilla-Befehle zur Blockbearbeitung wie `/setblock`, `/fill`, `/clone` und `/place` sowie WorldEdit-Befehle der Form `//...` umgehen das Platzierungslimit. Nach erkannten Bearbeitungsbefehlen wird der Zählcache geleert, damit spätere Prüfungen die geänderten Chunks neu scannen.

## Blocknamen

Blöcke werden mit ihrem Minecraft-Namen bezeichnet — also mit denselben Namen wie bei `/give` und `/setblock`. Ein hopper heißt beispielsweise `hopper`, Gras `grass_block` und ein Diamantblock `diamond_block`. Diese Namen erscheinen auch in `/chunklimit list` und in den Meldungen beim Setzen, Entfernen, Prüfen oder Erreichen eines Limits.

Die Tab-Vervollständigung schlägt namespaced IDs wie `minecraft:hopper` oder `minecraft:diamond_block` vor. Die Eingabe `dia` oder `minecraft:dia` schlägt jeweils `minecraft:diamond_block` vor.

Befehle und `config.yml` akzeptieren den Minecraft-Namen, den Bukkit-Materialnamen (`HOPPER`) und die namespaced ID (`minecraft:hopper`). Bestehende Konfigurationen bleiben daher kompatibel. Konfigurationseinträge werden beim nächsten Ändern eines Limits in Minecraft-Schreibweise zurückgeschrieben.

## Erstellen und Installieren

Benötigt Java 21 und Maven. Führe in diesem Verzeichnis folgenden Befehl aus:

```sh
mvn package
```

Kopiere `target/ChunkCap.jar` in den Ordner `plugins/` deines Servers und starte den Server anschließend neu. Das Projekt verwendet die Paper-1.21.11-API und Java 21.

## Zählverhalten

Beim ersten Prüfen eines Chunks werden dessen Blöcke gescannt. Vor dem Durchsetzen eines Limits wird der Ziel-Chunk erneut gezählt. Mit `/chunklimit antilag true` verwendest du für deine eigenen Platzierungswarnungen einen partikelsparenden, statischen Umriss in deiner gewählten Farbe. Mit `false` wechselst du zurück zum animierten, blockförmigen Umriss in deinem gewählten Thema. Alle persönlichen Einstellungen werden anhand der UUID in `plugins/ChunkCap/player-settings.yml` gespeichert.

Der Zählcache wird nach Platzierungsversuchen (auch wenn sie abgebrochen wurden), Abbau, Explosionen, Feuer, Blockumwandlungen, Kolbenbewegungen, Wachstum, Düngen, Ausbreitung, Änderungen durch Entitäten und erkannten Blockbearbeitungsbefehlen geleert. Doppelte Blöcke werden nur einmal gezählt: Bei Türen und anderen zweiteiligen Blöcken zählt die untere Hälfte, bei Betten der Fußteil. Das Limit wird bei Spielerplatzierungen und Blockumwandlungs-Events durchgesetzt.
