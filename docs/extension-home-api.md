# Home dichiarative delle estensioni — API v1

L'app non contiene adattatori, package, nomi di fonti o filtri specifici di un sito. Una fonte opzionale pubblica le proprie capacità nel suo APK, nell'asset `assets/aniyomi/home-v1.json` (nel progetto Android: `src/main/assets/aniyomi/home-v1.json`). Le estensioni senza questo asset continuano a funzionare in Esplora senza aggiungere una Home.

## Contratto

```json
{
  "version": 1,
  "homes": [{
    "id": "films",
    "title": "Film",
    "source": { "name": "Ciao", "lang": "it" },
    "defaults": { "Ordina": "Più visti" },
    "sections": [
      { "id": "popular", "title": "Più visti" },
      { "id": "recent", "title": "Novità", "filters": { "Ordina": "Più recenti" } }
    ],
    "search": { "id": "search", "title": "Cerca film", "filters": {} },
    "categories": { "filter": "Genere", "exclude": ["Tutti"] }
  }]
}
```

Le chiavi e i valori di `defaults`/`filters` corrispondono alle etichette pubbliche di `AnimeFilter.Select<String>`. L'app usa il contratto `getFilterList`/`getSearchAnime` già disponibile, senza riflessione su classi private. Ogni richiesta crea filtri nuovi. Una sezione che richiede filtri non supportati viene esclusa, non trasformata in una ricerca generica.

`source.name` e `source.lang` devono individuare una sola fonte appartenente all'estensione che contiene il file. Non autorizzano a impersonare una fonte di un altro package. Restano validi inizializzazione, compatibilità API, installazione, lingua abilitata, fonti nascoste e controlli NSFW.

## Home condivise

L'identificatore semantico `homes[].id` è condiviso tra estensioni: per contribuire alla stessa Home devono dichiarare lo stesso ID, per esempio `cartoons` o `films`. Non si raggruppa tramite il testo tradotto del titolo. Non esiste una whitelist di ID nell'app.

- Una sola voce di navigazione per ID, con contributi di tutte le estensioni disponibili.
- Le sezioni con lo stesso ID si uniscono; quelle esclusive si aggiungono. Concordare la semantica degli ID tra produttori: `popular` non deve significare "episodi appena usciti".
- Anche ricerca e categorie aggregano solo le fonti che le supportano.
- Risultati alternati tra fonti, preservando gli ordinamenti interni. I doppioni vengono rimossi per identità concreta `source + url`, mai fondendo opere diverse soltanto perché hanno lo stesso titolo.
- Ogni scheda conserva la fonte per episodi, libreria e riproduzione. Nessuna selezione obbligatoria della fonte per aprire la Home.
- Una fonte lenta o in errore non nasconde i risultati delle altre. Gli errori riportano il produttore interessato; la paginazione tiene conto delle fonti già esaurite.
- Rimuovendo una fonte spariscono i suoi contributi; la Home sparisce solo quando non rimane nessun produttore disponibile. I collegamenti aperti tornano alla Home quando il gruppo non esiste più.

Il nome visualizzato in caso di titoli discordanti viene scelto deterministicamente ordinando le identità dei produttori. Gli ID del produttore includono package, ID Home e ID fonte, per evitare collisioni nelle cache. La revisione include versione dell'estensione e contenuto della dichiarazione.

## Limiti e privacy

Solo lettura locale dell'APK scelto dal loader esistente, anche per installazioni private; nessuna richiesta remota durante il rilevamento delle capacità. Il documento ha un limite di 64 KiB decompressi, 8 Home, 16 sezioni per Home e 100 caratteri per etichetta. ID duplicati nella stessa estensione sono ambigui e ignorati. Versioni sconosciute o documenti malformati non bloccano il catalogo generale. Gli attributi aggiuntivi non eseguono codice e non avviano richieste.

Concorrenza condivisa: massimo 3 richieste alle fonti. Cache in memoria, 16 pagine per produttore, fino a 8 servizi conservati, scadenza 30 minuti. I feed pubblici persistono anche nel database Discovery separato: massimo 128 pagine consultate, conservazione massima 24 ore. Un riavvio entro la scadenza non riscarica i feed già salvati; dopo 30 minuti vengono mostrati subito e aggiornati in background. Il refresh manuale resta disponibile. La revisione dell'estensione invalida le vecchie pagine.

Il database salva solo ID locali e paginazione: flag della libreria e metadati vengono riletti, non ripristinati da vecchie copie. La migrazione 1 → 2 aggiunge una tabella senza alterare catalogo e collegamenti esistenti. Errori della cache non impediscono l'accesso alla rete. Le ricerche non vengono persistite; in incognito nessuna lettura o scrittura della cache, nemmeno in memoria. Solo download impedisce letture di queste pagine e richieste alle fonti. Continua a guardare e aggiornamenti usano gli interactor locali filtrando l'insieme delle fonti del gruppo.

## Compatibilità degli aggiornamenti

I vecchi collegamenti serializzati hanno un piccolo ponte verso la Home neutra. La vecchia selezione booleana non viene convertita con un cast nel nuovo identificatore testuale. Anime4K, package dell'app, firma e identità delle opere locali rimangono invariati.

La fiducia automatica è una preferenza separata, attiva per default in questo fork, comune ad anime e manga. Non modifica i consensi espliciti né i controlli di firma Android o versione API; disattivandola e riavviando si ripristina la conferma manuale.
