# Affidabilita e verifiche dell'app

## Ricerca
Le ricerche globali anime e manga condividono un coordinatore con cinque richieste
in corso al massimo, timeout per fonte, annullamento e identificazione delle richieste.
Una risposta superata non puo pubblicare risultati, anche se un'estensione ritarda
l'annullamento. Gli aggiornamenti dello stato sono atomici e ogni errore puo essere
ritentato individualmente, senza ricaricare le altre fonti. Non vengono creati pool
di thread per ogni schermata. Le copertine usano identita stabili.

## Backup e aggiornamenti
La rotazione conserva quattro backup, incluso quello appena verificato, e avviene
dopo scrittura e decodifica. Il nome include secondi e millisecondi e rimane
compatibile con i nomi precedenti per la rotazione. Gli errori automatici compaiono
in Dati e archiviazione; la prima anomalia e poi una ogni tre insuccessi producono
una notifica. La cancellazione del lavoro non viene registrata come errore.

L'updater distingue successo, errore e nuovo tentativo. I trasferimenti temporanei
appartengono alla singola richiesta; un APK viene proposto solo dopo verifica della
lunghezza disponibile e del package. Gli errori temporanei hanno al massimo due
nuovi tentativi; annullamenti ed errori permanenti non diventano successi.

## Download anime
I file HTTP MP4, M4V, MKV e WebM senza tracce o opzioni esterne possono mantenere
un file parziale nella cartella dell'episodio. La ripresa richiede la stessa identita
della richiesta, un ETag forte e una risposta Content-Range coerente. Una risposta
200 ricomincia il file; nessun blocco di un'altra rappresentazione viene accodato.
In assenza di validatore si ricomincia. Il trasferimento controlla lo spazio prima
della copia e periodicamente durante la scrittura.

Manifest HLS/DASH, torrent, tracce esterne e opzioni FFmpeg mantengono il percorso
esistente. Non e una promessa di ripresa universale per ogni servizio.
Il recupero dei collegamenti scaduti avviene tramite l'estensione installata,
una sola volta per ciclo di tentativi. Non viene introdotta conoscenza dei siti.
I downloader esterni mantengono il proprio flusso di consegna.

## Ripresa e interfaccia
Nascondere un titolo da Continua a guardare modifica esclusivamente una preferenza
locale. Cronologia, episodi visti, segnalibri e avanzamento non sono cancellati.
Annulla ripristina il singolo titolo; il comando permanente ripristina tutti i titoli
nascosti. Il filtro vale per la Home del catalogo e per le Home delle estensioni.
La riga rimane in entrambe le sezioni Anime e Cartoni.

Copertine e titoli della Home si adattano al carattere ingrandito; le intestazioni
hanno semantica accessibile e i controlli di ricerca lasciano spazio ai nomi lunghi.
Le schede dei pannelli adattivi, incluso il lettore manga, possono scorrere senza
comprimere tutte le etichette in una sola riga.

## Prestazioni e prove ripetibili
Il package per misurare e xyz.jmir.tachiyomi.mi.anime4k.benchmark, separato dalle
app installate. La variante contiene un'attivita esclusiva per preparare 500 anime,
501 manga, copertine e otto pagine locali sintetiche, piu il catalogo in cache.
Quell'attivita non fa parte degli APK preview/release.

Il modulo macrobenchmark copre avvio, Home, scorrimento delle librerie, ricerca
e apertura del lettore. Il workflow app_performance.yml conserva metriche,
tracce e profili. Un profilo prodotto dall'esecuzione deve essere esaminato prima
di sostituire app/src/main/baseline-prof.txt. Non si inventano regole o miglioramenti
percentuali. Le misure di un emulatore non rappresentano autonomia, temperatura
o fluidita del dispositivo dell'utente.

Il profilo misurato dal run 34617849306 (commit ab5134f1208c97accac59e2079c8549205957bb0)
copre Home, librerie, ricerca e lettore. Le regole precedenti sono conservate e
deduplicate: 52.848 regole finali, con le otto regole delle fixture escluse.

## Diagnosi del player
Avvisi ed errori sono registrati anche senza Registrazione dettagliata. Il livello
verboso resta facoltativo e richiede il riavvio dell'app; permette di raccogliere
piu contesto in ADB durante un'anomalia sporadica.

Le opzioni Auto e Auto sicuro ora corrispondono ai rispettivi valori mpv. Il cambio
dei canali conserva i filtri audio personalizzati e gestisce soltanto il filtro
etichettato dell'inversione stereo. Queste correzioni non dimostrano la causa di un
blocco seguito da corruzione audio/video, che richiede log raccolti durante l'evento.

Riferimenti: [HTTP range](https://www.rfc-editor.org/rfc/rfc9110.html#name-range-requests),
[profili Android](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile).
