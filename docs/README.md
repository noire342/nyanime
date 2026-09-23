# Documentazione Nyanime

Le guide descrivono il codice presente nel repository. Alcune funzioni dipendono
da un'estensione compatibile, da un servizio esterno o dalle capacità del dispositivo.
I documenti tecnici in inglese conservano i nomi delle API e dei componenti.

## Per chi usa l'app

| Guida | Contenuto |
| --- | --- |
| [Primi passi e FAQ](getting-started.md) | Installazione, aggiornamenti, estensioni, preferenze e problemi comuni. |
| [Aiuto](support.md) | Archiviazione, migrazione, tracker e risoluzione dei problemi. |
| [Privacy](privacy.md) | Dati locali, connessioni esterne e controlli disponibili. |
| [Tutte le funzionalità](features.md) | Catalogo delle funzioni video, manga, librerie, rete e dati. |
| [Interfaccia](nyanime-ui.md) | ModernUI, ritorno alla legacy, tema manga e copertine. |
| [Smart e timer](player-startup-and-sleep.md) | Avvio di Anime4K, timer, autoplay e stanze. |
| [Traduzione manga offline](manga-translator.md) | Modelli facoltativi, confronto delle pagine e glossario personale. |
| [AniSkip](aniskip.md) | Attivazione, associazione del titolo e segmenti disponibili. |
| [Novità Nyanime](../CHANGELOG.md) | Modifiche del fork e collegamento allo storico upstream. |

## Funzioni e architettura

| Guida | Contenuto |
| --- | --- |
| [Home e scoperta](discovery-home.md) | Cataloghi, associazione alle fonti, calendario, cache e ripresa. |
| [API Home anime](extension-home-api.md) | Contratto dichiarativo generico delle estensioni. |
| [API Home manga](manga-home-api.md) | Sezioni manga, capitoli, classifiche e identità degli elementi. |
| [Anime4K Smart](anime4k-smart.md) | Misure di rendering, preset, fallback e shader. |
| [Guarda insieme](watch-together.md) | Stanze cifrate, sincronizzazione, inviti e verifiche. |
| [I miei dispositivi (disattivato)](personal-sync.md) | Sync rimosso dall’app e pulizia dei suoi dati sul dispositivo. |
| [Community dormiente](community-protocol.md) | Codice social conservato, disabilitato nell’app. |
| [Cast](casting.md) | Google Cast, UPnP/DLNA, telecomando, relay locale e limiti. |
| [Affidabilità](app-reliability.md) | Ricerca, download, backup, copertine e prestazioni. |
| [Aggiornamenti delle estensioni](extension-update-alerts.md) | Frequenza dei controlli e deduplicazione delle notifiche. |
| [Regressioni del player](stability-regressions.md) | Verifiche native e del ciclo di vita del player. |

## Sviluppo e provenienza

- [Contribuire e compilare](../CONTRIBUTING.md).
- [Traduzioni](../i18n/README.md).
- [Benchmark](../macrobenchmark/README.md): misurazioni dedicate.
- [Dipendenze conservate localmente](../vendor/README.md).
- [Manutenzione FFmpeg e build nativa](../tools/native/README.md).
- [Crediti e licenze](credits.md).
- [Changelog storico AniYomi](history/aniyomi-changelog.md): archivio upstream,
  non elenco delle release Nyanime.
