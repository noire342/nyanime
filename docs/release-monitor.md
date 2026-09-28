# Uscite, aggiornamenti e calendario

Il monitoraggio è attivo inizialmente. Usa i titoli in libreria, tracciati, già
iniziati o seguiti esplicitamente. `Segui` nella scheda permette di includere
anche un titolo fuori dalla libreria; `Smetti di seguire` esclude quel titolo
dai controlli automatici. Restano rispettate le categorie escluse, le fonti
disabilitate, i titoli nascosti e la modalità incognito.

La voce **Uscite**, accanto a Libreria, apre **Le tue uscite**. Anime e manga
condividono l'agenda con filtri Tutti/Anime/Manga. Impostazioni > Libreria >
Agenda e calendario unificati permette di consultarli separatamente.
La schermata legge subito i dati locali; apertura e cambio giorno/mese non
attendono una verifica completa in rete. Il monitor aggiorna in background le
date con una rotazione limitata dei controlli.

La vista **Agenda** mostra le schede raggruppate per giorno. Il selettore
**Agenda / Calendario** permette di passare alla griglia mensile: ogni giorno
è selezionabile, anche se vuoto. Tornando alla lista viene conservato il giorno
scelto come punto di partenza della sequenza cronologica. All’apertura l’agenda
parte da oggi: le uscite precedenti sono sopra e quelle future sotto, senza
un limite di sette giorni. Il comando **Oggi** riporta alla posizione attuale.
Filtri e selettore restano accessibili durante lo scorrimento; le nuove verifiche
non riportano la lista all’inizio. Anche un giorno di apertura vuoto conserva
un punto nella sequenza, senza inventare uscite o date mancanti.

## Due eventi distinti

- **Nuovo contenuto disponibile:** una lista restituita dall'estensione contiene
  un episodio/capitolo nuovo. Viene salvato insieme al suo avviso, nella stessa
  transazione. Le tue novità, gli aggiornamenti e i widget usano i dati esistenti.
- **Trasmissione originale annunciata:** una data pubblica AniList, associata
  tramite ID AniList/MAL verificato. Non equivale alla disponibilità nella fonte.
  Il calendario non trasforma una frequenza stimata in una data di pubblicazione.
  [Dati di trasmissione AniList](https://docs.anilist.co/reference/object/airingschedule).

I manga mostrano le nuove disponibilità reali. Le date future richiedono il
contratto facoltativo `ReleaseScheduleProvider` dell'estensione; in assenza di
date annunciate il calendario lo indica, senza inventare appuntamenti.

## Controlli e recupero

WorkManager verifica la coda ogni 15 minuti, con al massimo 24 titoli per giro,
tre fonti in parallelo e richieste sequenziali per fonte. Le code grandi
proseguono in giri successivi; non vengono tagliate definitivamente. Il controllo
ordinario è ogni sei ore; vicino alla trasmissione è ogni 15 minuti, poi ogni ora
per le prime 48 ore di attesa. Un anime concluso viene controllato meno spesso
soltanto con stato concluso e numero totale confermati e tutti gli episodi presenti.
Le schede e Continua a guardare possono anticipare un controllo dopo dieci minuti.

Successo, tentativo e prossimo controllo sono persistenti e distinti. Gli errori
mantengono il successo precedente e usano attese crescenti. Riavviare l'app non
fa perdere il lavoro completato. Le richieste allo stesso titolo coordinano il
recupero e il salvataggio; le richieste al calendario rispettano la risposta 429
e la relativa attesa. Il ritmo rispetta i
[limiti documentati di AniList](https://docs.anilist.co/guide/rate-limiting).
La prima acquisizione di un titolo non pubblica tutto il catalogo come novità.
Gli avvisi storici importati dalla prima migrazione vengono esclusi dall'agenda,
senza cancellare capitoli, episodi o progressi. Una data fornita effettivamente
dall'estensione viene conservata separatamente dalla data di rilevamento.
Anche i controlli senza modifiche verificano le date degli avvisi esistenti.
Una disponibilità priva di data resta nella Home/Novità, senza inventare un giorno
nel calendario. La correzione degli avvisi precedenti usa la normale rotazione
limitata delle richieste; le date sostitutive del catalogo non sono prove di pubblicazione.

Le notifiche usano una coda persistente, con deduplicazione e identificatori
Android stabili. Se il permesso o il canale sono disabilitati, l'avviso resta
in attesa. Lo stato e il test delle notifiche sono in Impostazioni > Libreria.
Gli avvisi dei titoli esclusi vengono scartati. Date e impostazioni sono locali;
le preferenze per titolo entrano nel backup mediante URL e ID opaco della fonte,
non mediante gli ID numerici di un altro database.

## Promemoria e limiti verificabili

Un solo allarme locale punta alla prossima trasmissione. L'autorizzazione Android
per gli allarmi puntuali è facoltativa; senza di essa il promemoria può ritardare.
Date non più annunciate vengono rimosse soltanto dopo una risposta completa e
valida, con numero e ora aggiornati insieme. Errori e pagine incomplete conservano
l'ultima verifica riuscita. Gli orari seguono il fuso del dispositivo.

La disponibilità non può precedere la pubblicazione dell'estensione. I controlli
periodici dipendono dalla rete e dalla pianificazione Android; un arresto forzato
dell'app impedisce l'esecuzione fino alla riapertura. Non serve un token aggiuntivo.
AnimeSchedule non fa parte di questa implementazione.

## Verifiche

- `ReleasePolicyTest`: finestre temporali, recupero da errori, decodifica delle
  date, formato dei backup e coordinamento/cancellazione degli aggiornamenti.
- `scripts/tests/test_release_monitor_schema.py`: migrazioni effettive su SQLite,
  rollback della coda, invii duplicati, esclusioni, visto/letto e cancellazione.
- Le prove reali di layout e notifiche sono distinte dalle prove delle migrazioni;
  l'esecuzione pianificata a schermo spento va verificata per ogni dispositivo.

## Unione delle uscite tra fonti

L'agenda anime riusa `mergeHomeCards`: ID di catalogo compatibili, oppure titolo
normalizzato e anno entrambi noti, senza conflitti tra identificativi. I dati di
tracking e calendario arricchiscono il contratto generico delle estensioni;
evidenze contraddittorie impediscono l'unione. Un episodio con numero riconosciuto
compare una sola volta e offre le fonti concrete disponibili. Numeri sconosciuti,
stagioni con ID diversi non vengono accorpati. Le edizioni nella stessa fonte
possono condividere l'annuncio solo con ID comuni verificati, mai per il solo
titolo/anno; la scelta resta esplicita e non cambia il comportamento della Home.
La disponibilità prevale sull'annuncio e un episodio già visto in una variante
non resta un'uscita da vedere nell'altra. Le date di pubblicazione dei capitoli
restano separate dal rilevamento nell'app anche durante la riparazione dei dati.
