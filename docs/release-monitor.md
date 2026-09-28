# Uscite, aggiornamenti e calendario

Il monitoraggio è attivo inizialmente. Usa i titoli in libreria, tracciati, già
iniziati o seguiti esplicitamente. `Segui` nella scheda permette di includere
anche un titolo fuori dalla libreria; `Smetti di seguire` esclude quel titolo
dai controlli automatici. Restano rispettate le categorie escluse, le fonti
disabilitate, i titoli nascosti e la modalità incognito.

La vista **Agenda** mostra le schede raggruppate per giorno. Il selettore
**Agenda / Calendario** permette di passare alla griglia mensile: ogni giorno
è selezionabile, anche se vuoto. Tornando alla lista viene conservato il giorno
scelto; Prossimi 7 giorni riporta alle uscite da oggi.

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
