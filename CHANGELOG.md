# Novità Nyanime

Questo file descrive le modifiche del fork Nyanime. Le funzioni ereditate e mantenute
sono incluse nel [catalogo completo](docs/features.md). La documentazione corrente
è raccolta nell'[indice](docs/README.md).

Le revisioni `rNNNN` derivano dal numero dei commit: non sono versioni semantiche
né il `versionCode` Android. Gli hash qui sotto identificano modifiche nel repository,
non garantiscono che ogni commit sia stato distribuito come APK.

## 28 settembre 2026 — Scegliere la stagione dal manga

- La scelta degli anime collegati a un manga mostra i capitoli documentati di
  inizio e fine per ogni stagione, in un riquadro compatto sotto titolo e anno.
  Sono conservati anche i riferimenti a una pagina interna al capitolo.
- I due percorsi anime/manga condividono dati e cache di catalogo. Film, speciali
  e stagioni senza un riferimento pertinente non ereditano intervalli di altre
  stagioni; l'apertura del titolo continua a verificarne gli ID nelle estensioni.

## 28 settembre 2026 — Home torna prima in cima

- Toccando di nuovo Home, una pagina scorsa torna in cima; il cambio categoria
  avviene soltanto quando si è già all'inizio della pagina.
- Lo stesso comportamento vale per le Home video, Manga e catalogo. Durante
  il ritorno in cima, tocchi ripetuti non cambiano categoria.

## 28 settembre 2026 — Collegamenti anime e manga sempre compatti

- Rimossi il pulsante di espansione e i dettagli estesi. Restano il gradiente,
  i riferimenti di stagione e capitolo e l'apertura diretta del titolo.
- Se sono disponibili più adattamenti o copie, una breve scelta al tocco conserva
  tutte le destinazioni senza ingrandire la scheda.

## 28 settembre 2026 — Dettagli manga senza ripetizioni

- La scheda espansa mantiene i riferimenti compatti di stagione, inizio e fine
  una sola volta. Rimossi i paragrafi duplicati dai dettagli; restano le copie
  alternative, la copertina e le azioni di apertura.

## 28 settembre 2026 — Capitoli della stagione aperta

- La scheda compatta anime/manga mostra due righe con stagione, inizio/fine e
  capitolo documentato. Il numero si riferisce alla stagione aperta nella scheda.
- Riconosciuti anche i riferimenti «S1» senza episodio e i campi con più stagioni:
  i capitoli di un'altra stagione o dell'intera serie non vengono riutilizzati
  come fine di un sequel. Gli archi con riferimenti pertinenti hanno la precedenza.
- Conservati il gradiente, l'apertura diretta del manga e il controllo circolare
  per espandere e richiudere i dettagli.

## 28 settembre 2026 — Riferimenti manga a colpo d'occhio

- La scheda compatta mostra subito il capitolo iniziale e l'ultimo capitolo
  documentato, quando disponibili. Il gradiente è condiviso con la versione
  espansa, mantenendo un solo controllo circolare per aprire e richiudere i dettagli.
- Il passaggio dal manga all'anime verifica l'identità della copia nei metadati
  dell'estensione: vecchie associazioni di tracking da sole non fanno comparire
  adattamenti diversi tra le copie dell'anime.

## 28 settembre 2026 — Dal manga all'anime

- Collegamenti anime/manga compatti di default: toccare la scheda apre il titolo,
  il pulsante circolare espande i dettagli e rimane in basso a destra per richiuderli.
  Il capitolo viene mostrato solo quando esiste un punto di continuazione verificato.
- Le schede manga mostrano gli anime collegati tramite ID di catalogo, senza
  richiedere libreria o tracking. Le estensioni compatibili possono aprire una
  copia verificata direttamente; stagioni, film e altri adattamenti restano
  distinti e selezionabili.
- I collegamenti attraverso la novel originale sono indicati esplicitamente.
  Non viene inventato un episodio dal numero del capitolo: aprire la scheda
  anime conserva il progresso già presente.
- La nuova card conserva il suo stato durante la navigazione e rispetta le
  animazioni ridotte. Un errore del catalogo permette di riprovare nella scheda.

## 28 settembre 2026 — Manga per archi delle opere nate come novel

- Quando un anime deriva da una novel, Nyanime mostra anche i manga collegati
  alla stessa opera, distinguendo il rapporto indiretto da un adattamento diretto.
  Gli intervalli stagione/episodio documentati aiutano a scegliere l'arco, senza
  usarli per indovinare un capitolo esatto.
- Corretto il mantenimento dei contratti di ricerca nelle build ottimizzate:
  i collegamenti forniti dalle estensioni possono aprire anche manga mai aggiunti
  alla libreria e senza un tracking già impostato.
- Le estensioni che includono copie delle interfacce facoltative usano ora la
  definizione fornita dall'app, così la ricerca per ID e i link diretti vengono
  riconosciuti anche dopo il caricamento degli APK.
- Tornando dal manga, la card dell'anime conserva copertina, copie e riferimenti
  già caricati. Le richieste in corso proseguono con la scheda e gli aggiornamenti
  non rimuovono temporaneamente i risultati precedenti.

## 28 settembre 2026 — Dall'anime al manga

- La scheda anime riconosce i manga collegati tramite ID di catalogo e, quando
  l'estensione lo fornisce, apre direttamente una copia verificata senza cercarla
  per titolo.
- I punti di inizio e fine adattamento compaiono quando sono documentati. Il
  passaggio a un capitolo preciso è proposto soltanto se il riferimento
  all'episodio è esplicito; non vengono stimate corrispondenze mancanti.
- Le estensioni possono fornire ricerca per ID e link correlati tramite contratti
  generici, mantenendo nell'estensione ogni logica del rispettivo sito.

## 27 settembre 2026 — Categorie Home adattive

- Le categorie della Home si distribuiscono su una o due righe secondo la larghezza
  dello schermo. Se sono ancora troppe, restano raggiungibili scorrendo lateralmente.
- Una pressione lunga porta una categoria all'inizio e conserva l'ordine scelto.
  Toccare nuovamente Home nella barra inferiore passa alla categoria successiva,
  tornando alla prima dopo l'ultima.

## 27 settembre 2026 — Esplorazione manga e filtro lingue

- La sezione personale si chiama ora «Libreria». Nella Home manga i generi sono
  subito disponibili sotto l'intestazione, con chip e ricerca nell'elenco completo.
- Etichette equivalenti in italiano e inglese confluiscono in un solo genere;
  ogni fonte riceve comunque il proprio filtro originale.
- La ricerca usa una corsia separata dai caricamenti della Home e presenta i
  risultati di ogni fonte appena arrivano, senza attendere le altre.
- La preferenza già presente per le altre lingue ora vale anche in ricerca,
  elenco fonti, Libreria, cronologia e novità manga. Nascondere un titolo non
  cancella letture, download o dati salvati.

## 27 settembre 2026 — Compatibilità completa con le estensioni manga 1.6

- Le schede manga, la biblioteca, il tracking, la ricerca e le stanze usano ora
  l'aggiornamento combinato di dettagli e capitoli delle estensioni 1.6.
  L'apertura di un titolo non invia più richieste ai vecchi endpoint separati.
- Le estensioni meno recenti continuano a funzionare tramite il contratto
  precedente. Il modello e i metodi delle nuove estensioni sono disponibili
  nell'API generica dell'app, senza logica legata a una fonte.
- Le richieste simultanee per lo stesso manga vengono coordinate, evitando
  aggiornamenti concorrenti tra scheda, Home e tracking.

## 27 settembre 2026 — Home Manga e librerie unite

- Manga entra nella Home moderna accanto alle categorie video, con sezioni, ricerca
  nel catalogo completo e filtri forniti dalle estensioni installate. I risultati
  di fonti diverse si uniscono soltanto quando condividono ID pubblici concordi;
  i titoli ambigui restano separati e la fonte predefinita si può cambiare.
- La barra inferiore riunisce la libreria anime e la biblioteca manga in
  «Biblioteca & Libreria». La vecchia interfaccia manga viene disattivata.
- Una preferenza nasconde dalla Home Manga i cataloghi non italiani, senza
  rimuovere titoli già presenti nella biblioteca personale.
- Il tracking manga continua ad avviarsi dalla lettura del primo capitolo;
  i tentativi simultanei sullo stesso titolo vengono serializzati per evitare
  associazioni duplicate.
- L'interfaccia generica delle estensioni manga ora conserva i metadati
  temporanei richiesti dai client più recenti per caricare titoli e capitoli.

## 27 settembre 2026 — Prossima uscita nella scheda del titolo

- Il tempo che manca al prossimo episodio compare anche sotto lo studio,
  prima dello stato e della fonte. Usa lo stesso conto alla rovescia della lista
  episodi e rispetta la preferenza che ne controlla la visibilità.
- Un refresh conserva la data nota se il tracker non è ancora disponibile o la
  richiesta alla rete fallisce. La aggiorna soltanto dopo una risposta valida.

## 27 settembre 2026 — Prossima uscita degli episodi

- La previsione del prossimo episodio è ora una scheda discreta nella lista:
  mette in primo piano data e ora locali, seguite dal tempo rimanente; il numero
  e il titolo dell'episodio restano leggibili sotto, senza la vecchia scritta rossa.
  Il tempo si aggiorna quando cambia il minuto e la scheda si adatta agli
  schermi stretti e alle griglie della UI moderna e legacy.

## 27 settembre 2026 — Home Anime con più fonti

- Le sezioni e la ricerca della Home Anime combinano i risultati delle estensioni
  installate. Le schede dello stesso titolo vengono unite quando condividono
  un identificatore di catalogo affidabile; le fonti restano selezionabili
  anche quando le schede arrivano in pagine diverse. Identificatori in
  conflitto impediscono la fusione.
- Un tocco apre subito la fonte predefinita. Il selettore sulla scheda permette
  di cambiarla e, se desiderato, ricordarla per quel titolo. I filtri mostrano
  le opzioni disponibili nelle fonti attive.
- Le estensioni possono fornire classifiche, un indice dei generi, titoli ed
  episodi casuali, l'episodio preciso di una scheda e titoli simili. Quando una
  Home usa più fonti, il logo di un singolo sito non sostituisce Nyanime.
- La barra dei generi mostra di nuovo tutte le categorie disponibili. Le due
  scelte casuali sono raccolte in Esplora, fuori dalla barra dei generi.
- Uscendo dal player dopo l'apertura diretta di un episodio, la scheda conserva
  lo stato dell'avvio e non rilancia una seconda volta il player.

## 27 settembre 2026 — Tracking dei titoli nella libreria

- Un anime o manga aggiunto alla libreria viene cercato in background nei
  tracker configurati, anche prima del primo episodio o capitolo. Quando
  l'associazione è certa, lo stato iniziale resta «Da vedere» o «Da leggere»
  nei tracker che lo supportano e il progresso resta a zero finché non inizi
  davvero. Le associazioni già presenti non vengono duplicate.
- Un controllo correttivo, eseguito una sola volta, esamina anche i titoli
  non ancora iniziati che erano già in libreria. La voce «Riesamina la
  libreria» permette di riprovare manualmente i titoli rimasti senza un
  collegamento certo.

## 27 settembre 2026 — Aggiornamenti progressivi di episodi e Home

- Aprendo una scheda anime, l'elenco degli episodi salvato viene mostrato subito e
  controllato in sottofondo con la sua estensione, senza dover trascinare per aggiornare.
- Nella Home i titoli visti di recente vengono controllati a rotazione, compresi
  quelli che aspettano un episodio per riapparire in «Continua a guardare».
  Nessun titolo viene escluso perché la lista è lunga; quelli nascosti non
  partecipano. I titoli mai controllati o più arretrati passano per primi;
  a parità di attesa hanno precedenza le serie in corso. Limiti persistenti
  per titolo e per fonte evitano raffiche di
  richieste anche dopo il riavvio dell'app.
- Le sezioni della Home continuano a ricevere i dati dalle estensioni. Il controllo
  della libreria resta legato soltanto all'aggiornamento periodico configurato
  nelle impostazioni o all'azione manuale. Il controllo automatico prosegue
  con piccoli lotti a distanza di almeno un'ora finché ha esaurito i titoli
  arretrati; dopo una lunga assenza riparte alla riapertura. Usa lo stesso
  limite per fonte. Quello manuale resta completo quando viene richiesto
  esplicitamente.
- Un vecchio controllo generale avviato automaticamente dalla Home viene fermato
  dopo l'aggiornamento, senza interrompere i controlli periodici o manuali.

## 26 settembre 2026 — Riesame manuale del tracking

- In Impostazioni → Tracking, «Riesamina i titoli iniziati» riprova anime e manga
  guardati o letti che non sono ancora collegati ai servizi configurati. Si può
  avviare anche dopo il recupero iniziale e con il tracking automatico spento.
- I collegamenti già presenti restano intatti. La schermata mostra l'avanzamento,
  i nuovi collegamenti e se il controllo è stato interrotto.

## 26 settembre 2026 — Riproduzione in finestra

- Il video continua a riprodursi quando si passa alla modalità picture-in-picture:
  la pausa avviene solo quando il player esce davvero dallo schermo.
- Corretto il ridimensionamento che poteva lasciare metà finestra nera al primo
  ingresso in picture-in-picture, specialmente uscendo dal player orizzontale.

## 25 settembre 2026 — Schermata Novità

- Lo scorrimento orizzontale tra Anime e Manga resta continuo: il tema della
  pagina manga non ricrea più l'intera schermata a metà gesto.
- Filtri e stato vuoto hanno una presentazione più chiara, con conteggi separati
  per i titoli ancora da vedere o leggere e per tutti gli episodi o capitoli.

## 25 settembre 2026 — Installazione degli aggiornamenti

- Quando il download OTA termina, un avviso nell'app offre «Installa ora» e
  «Non ora». L'APK pronto resta accessibile in Altro anche dopo aver lasciato
  la schermata delle novità; l'avviso scompare quando la versione è installata.

## 25 settembre 2026 — Novità nella Home

- Un indicatore circolare appare nella Home anime o manga soltanto per novità non
  ancora viste. Ha il colore delle altre icone della barra superiore. Toccandolo,
  la pagina scorre fino alla sezione «Le tue novità».
- Per impostazione iniziale l'indicatore si spegne anche quando la sezione entra
  nello schermo scorrendo a mano; questa scelta si può disattivare nelle
  impostazioni Libreria. I titoli restano nella sezione finché non vengono
  aperti o ignorati; le novità arrivate dopo riattivano l'indicatore.

## 25 settembre 2026 — Recupero iniziale e sigle personalizzate

- Se un tracker è già configurato, Nyanime collega in background anche i titoli
  iniziati prima dell'aggiornamento. Il recupero si conclude una sola volta per
  installazione; se viene interrotto, riprende al successivo avvio dell'app.
- In Altro del player si sceglie se saltare la sigla automaticamente come regola
  generale e, per l'anime aperto, se ereditarla oppure fare un'eccezione.
  La scelta viene applicata senza riavviare il video.
- Quando viene associato un titolo già visto o letto, il progresso locale
  riconosciuto viene riportato al tracker anche in presenza di episodi o
  capitoli non consecutivi.

## 25 settembre 2026 — Collegamento automatico ai tracker

- Quando inizia la visione o la lettura, Nyanime collega in background il titolo
  ai tracker già configurati sul dispositivo e aggiorna poi il progresso.
  La ricerca usa prima gli identificativi forniti dall'estensione, poi titoli
  alternativi e corrispondenze prudenti; risultati ambigui restano da confermare.
- AniSkip può ricavare l'identificativo dell'anime senza richiedere prima un
  collegamento manuale al tracker. Le impostazioni disattivate esplicitamente
  restano rispettate.
- Nelle nuove installazioni l'aggiunta alla libreria non apre più di default
  la finestra di collegamento manuale. La scelta già salvata resta invariata.

## 25 settembre 2026 — Novità personali e aggiornamenti nell'app

- Le schermate Aggiornamenti già esistenti mostrano per prime le novità da
  vedere o leggere dei titoli nella libreria e di quelli guardati o letti di recente.
  Raggruppano le novità per titolo, così molti episodi o capitoli della stessa
  opera non riempiono l'elenco. Aprire o ignorare un titolo lo toglie dalle
  novità correnti; la scheda Tutti mantiene la cronologia completa.
- Nelle Home, una campanella apre Novità; la sezione orizzontale «Le tue novità»
  compare soltanto quando ci sono episodi o capitoli pertinenti da scoprire.
  Il controllo periodico include fino a 20 titoli recenti della cronologia anche
  se non sono nella libreria, senza avviare download automatici per questi titoli.
  I contenuti già presenti prima della prima visione o lettura non vengono
  scambiati per nuove uscite.
  La ricerca delle novità
  usa la data in cui l'app ha rilevato episodi e capitoli nuovi, anche quando
  la fonte non indica una data di pubblicazione affidabile.
- Il calendario evidenzia il giorno selezionato e mostra subito quante uscite
  sono previste, anche quando il giorno scelto è vuoto.
- Nella schermata Info si può attivare il download dell'aggiornamento nell'app:
  avanzamento visibile, possibilità di annullare o riprovare e pulsante
  Installa aggiornamento al termine. L'opzione è attiva inizialmente e conserva
  la scelta di chi l'ha già disattivata. Android chiede comunque la conferma.

## 24 settembre 2026 — Stanze: avvio preparato e durate dei video

- L'owner può attivare nelle opzioni della stanza il precaricamento prima del
  primo avvio di ogni episodio. Ogni telefono carica in parallelo fino a 15
  secondi di video; se la stima non è disponibile, usa un margine breve.
  L'attesa massima per il precaricamento è 15 secondi.
- Le piccole differenze di durata riportate da manifest e qualità diverse dello
  stesso episodio non bloccano più la stanza. Durante l'apertura, una durata
  ancora sconosciuta viene mostrata come preparazione e non come video diverso.

## 24 settembre 2026 — Pulizia dei dati disattivati

- La pulizia dei vecchi dati di profili e sincronizzazione termina correttamente
  anche nell'APK ottimizzato, senza un avviso a ogni avvio.

## 23 settembre 2026 — Stanze con relay verificati

- Le stanze usano due relay che hanno superato una prova di scambio cifrato in
  entrambe le direzioni, senza account. I precedenti rifiutavano gli invii.
- La connessione viene mostrata come pronta solo dopo che il relay accetta un
  messaggio di prova; rifiuti e limiti di frequenza non fanno più lampeggiare lo
  stato della stanza come se fosse disponibile.
- Una stanza vuota non invia più aggiornamenti periodici inutili e le richieste
  di ingresso ripetute sono meno frequenti.
- Un singolo aggiornamento di presenza perso non ferma più entrambi i video;
  con l'opzione di attesa attiva, il buffering reale continua a mettere in pausa la stanza.
- Le nuove stanze tollerano fino a 5 secondi di caricamento dell'ospite
  prima di fermare anche chi ospita; chi era rimasto indietro si riallinea quando
  torna pronto. La pausa immediata per tutti resta disponibile nelle opzioni.

## 23 settembre 2026 — Inviti brevi coerenti in tutta la stanza

- Link, messaggio condiviso e QR del creatore ora contengono il codice stanza a
  otto cifre, anche quando si invita dal lettore manga. L'amico chiede di entrare
  e il creatore conferma l'accesso.
- La schermata delle stanze mette in primo piano Crea/Entra, codice e richieste;
  il nome facoltativo resta modificabile senza affollare il percorso iniziale.
- I link completi delle versioni precedenti continuano ad aprirsi.

## 23 settembre 2026 — Accesso alle stanze con codice breve

- Per invitare un amico in una stanza video o manga ora bastano otto cifre temporanee.
  Chi crea la stanza vede la richiesta e può accettarla o rifiutarla.
- L'ingresso mostra lo stato del collegamento; gli inviti completi già condivisi
  continuano a funzionare.

## 23 settembre 2026 — Rimozione della traduzione manga sperimentale

- Rimossi pulsanti, impostazioni e componenti della traduzione manga offline.
- Dopo l'aggiornamento, l'app elimina i modelli e la cache della traduzione scaricati
  in precedenza, senza alterare manga, progressi o annotazioni delle stanze.

## 23 settembre 2026 — Annotazioni condivise più affidabili

- Nella stessa stanza video e manga, schizzi e note sulla pagina si riallineano
  dopo disconnessioni e messaggi fuori ordine; gli interventi nascosti non ricompaiono.
- Ogni partecipante può nascondere e ripristinare i propri interventi per tutti;
  il creatore può moderare quelli della stanza. La visibilità locale resta separata.
- Dopo una chiusura imprevista, la schermata offre il rientro esplicito nella stanza
  con le annotazioni temporanee salvate in forma cifrata sul dispositivo.
- La stanza mostra temporaneamente a che punto del video stanno guardando gli altri.

## 23 settembre 2026 — Pressione prolungata nel player personalizzabile

- Nelle impostazioni dei gesti puoi scegliere cosa accade tenendo premuto sul video:
  velocità temporanea 2× (predefinita), 1,5× o 1,25×, cattura schermata oppure
  nessuna azione.
- Rilasciando il dito viene ripristinata la velocità che avevi scelto prima del
  gesto; la velocità temporanea resta disabilitata nelle stanze.

## 23 settembre 2026 — Backup trasferibili e note OTA per versione

- I nuovi backup manuali e automatici usano `.nyabk`; il ripristino continua ad
  accettare i vecchi file `.tachibk` e il formato interno resta compatibile.
- Esporta dati offre un backup completo di librerie, progressi e segnalibri di
  visione e lettura anche fuori dalla libreria, cronologia, impostazioni, preferenze delle
  fonti ed estensioni. Video e pagine scaricati restano esclusi.
- Sul nuovo dispositivo vengono ripristinate anche le preferenze non ancora
  inizializzate; categorie, impostazioni e titoli sono ripristinati nell'ordine
  corretto. Le categorie anime e manga sono mappate separatamente e le stagioni
  recuperano episodi e progressi, anche quando non erano state aggiunte
  singolarmente alla libreria.
- I titoli nascosti da “Continua a guardare” sono ricollegati sul nuovo telefono
  tramite riferimento alla fonte e al titolo, senza copiare ID del database.
- Le preferenze che non si riescono a ripristinare compaiono nel resoconto degli
  errori invece di essere ignorate senza avviso.
- Le note di aggiornamento OTA mostrano soltanto le novità aggiunte dal tag
  precedente, senza riproporre ogni volta l'intera sezione del changelog.

## 23 settembre 2026 — Licenze, aiuto e aggiornamenti OTA

- La testata Info ora mostra il marchio Nyanime senza lo spazio vuoto sopra l'icona.
- Nella Home moderna, logo, Stanze e Cerca restano sulla stessa riga anche sugli
  schermi stretti; il pulsante Cerca mantiene la sua forma a pillola.
- La pagina delle licenze delle librerie usa un elenco compatibile con la versione
  Compose dell'app, con ricerca e dettaglio della licenza, evitando il crash.
- Il pulsante Aiuto e le guide collegate aprono la documentazione Nyanime.
- La schermata di aggiornamento mostra le novità di questa release, prese dal
  changelog, anziché istruzioni tecniche per scegliere un APK.
- Gli APK delle nuove release mantengono nomi riconosciuti anche dalle versioni
  già installate, per consentire l'aggiornamento OTA diretto.

## 23 settembre 2026 — Link Nyanime e aggiornamenti OTA

- I pulsanti Sito web, Discord e GitHub nella schermata Info mostrano un avviso
  finché i relativi canali non saranno disponibili; rimossa la voce per tradurre.
- Licenze e privacy aprono i documenti di questo repository.
- Le release preview firmate vengono pubblicate automaticamente da `main` con APK
  per ogni architettura, riattivando la verifica degli aggiornamenti OTA.
- Supporto alle estensioni video con versione 17 della libreria, conservando le
  versioni precedenti e gli identificatori tecnici necessari alla compatibilità.

## 22 settembre 2026 — Sync disattivato e avvio Ultra più accessibile

- Sync personale rimosso dalle impostazioni e disattivato anche sulle installazioni
  già configurate. Pulizia automatica di chiavi, archivi privati e code di sync/community;
  librerie, progressi locali, download e stanze restano disponibili.
- Codice social e sync inutilizzato escluso dall’APK ottimizzato.
- Ultra può elaborare a schermo acceso e a batteria; le due restrizioni diventano
  facoltative. Soglie termiche meno prudenti, carico GPU ancora dosato e attese
  ricontrollate senza accumulare ritardi crescenti. Aggiornamento delle code esistenti.

## r8200 — Guarda insieme e controlli del player

Riferimenti: `bc660e1b0`, `7ee9e9a4f`, `51d484a1e`, `83115106a`.

- Play con risposta immediata, animazione play/cuore/cerchio e conto alla rovescia
  integrato nel video.
- Iniziali e stato dei partecipanti, indicazione di chi sta ancora caricando
  e messaggi brevi sulle azioni manuali.
- Scadenza dei comandi non confermati, nuovi tentativi controllati e riconnessione
  senza riapplicare richieste di episodi precedenti.
- Barra della stanza nella navigazione e nei dettagli, con episodio, partecipanti
  e ritorno al player.
- Caricamento individuale, inviti al salto e scheda del prossimo episodio più compatti,
  conservando legacy, riduzione del movimento e timer.
- Anime4K disabilitato per l'intera stanza, con ripristino delle scelte individuali
  all'uscita.
- Protezioni del ciclo di vita nativo durante apertura e chiusura, anche senza stanze.

Per questa revisione: 411 test automatici superati, quattro facoltativi saltati,
85 render Compose generati, build preview e controlli di firma/allineamento completati.
Non è stata eseguita una nuova prova con due telefoni su questa revisione.

## Stanze cifrate e sincronizzazione

Riferimenti: `2ec1bd009`, `51ba63dfb`, `6019330d5`.

- Creazione e ingresso tramite codice, link e QR, fino a otto partecipanti.
- Trasporto cifrato su relay Nostr, senza account Nyanime o apertura di porte.
- Episodio scelto dall'host e apertura automatica attraverso l'estensione locale.
- Play, pausa, seek e velocità condivisi, attesa della disponibilità,
  correzioni temporali graduali e recupero dopo perdita della connessione.
- Protezioni per timer, focus audio e background; salti e prossimo episodio coordinati.

## Home manga delle estensioni

Riferimento: `10fb2a381`.

- Home opzionale accanto alla biblioteca, basata sulle capacità dell'estensione.
- Aggiornamenti, classifiche, metadati di presentazione e azioni dei capitoli.
- Apertura del capitolo esatto e “Continua a leggere”.
- Lettore, tema manga, librerie e avanzamento mantenuti.

## ModernUI, navigazione e strumenti del player

Riferimenti: `27b30551a`, `f4455e11c`, `a7364253d`, `f5c1210ca`,
`73a899d03`, `9575ea5b3`, `f70647847`, `d91acfa62`, `b1f9a8e69`.

- Nome e icona Nyanime, presentazione video cinematografica e tema manga indipendente.
- ModernUI attiva di default, con ritorno alla presentazione legacy dalle impostazioni.
- Transizioni tra locandina e dettagli, ritorno, sfondi, testi e pannelli.
- Segnaposto delle copertine, aggiornamenti che conservano il contenuto e recupero
  delle immagini tramite l'estensione.
- Selettori centrati quando possibile e indicatore di refresh legato al trascinamento.
- Interruttore per l'avvio automatico di Smart, mantenendo l'attivazione manuale.
- Timer ridisegnato, durate rapide, memoria della durata personalizzata, estensione,
  fine episodio e priorità rispetto all'autoplay.
- Scheda del prossimo episodio e miglioramenti dell'integrazione AniSkip esistente.

## Cast e telecomando persistente

Riferimento: `c5ef563fe`.

- Google Cast e UPnP/DLNA, scoperta sulla rete locale e relay dei contenuti supportati.
- Telecomando, volume, avanzamento, coda episodi, notifica e navigazione durante il Cast.
- Cambio del video e ritorno alla riproduzione locale con posizione.
- Luminosità sui ricevitori DLNA che espongono il controllo; limiti documentati
  per codec, sottotitoli, DASH e shader locali.

## Scoperta, Anime4K e affidabilità

Il lavoro precedente comprende:

- Catalogo video, associazione automatica a titolo e stagione, fallback Kitsu,
  calendario e Home dichiarative delle estensioni.
- “Continua a guardare” indipendente dai feed remoti e gestione dei titoli nascosti.
- Anime4K integrato in mpv, Smart con misure del rendering, calibrazione,
  telemetria limitata e verifica degli shader.
- Ricerca globale con annullamento e concorrenza limitata, cache e retry per sezione.
- Download HTTP riprendibili quando validabili, controllo dello spazio,
  recupero dei link e protezioni dei backup.
- Aggiornamenti delle estensioni deduplicati, scelta corretta degli allegati APK.
- Correzioni PiP/JNI, demuxer HLS per ripresa e seek, accessibilità con testo grande,
  verifiche di rendering e strumenti di misura delle prestazioni.

Dettagli e vincoli sono nelle [guide tecniche](docs/README.md).

## Storico upstream

Il precedente changelog AniYomi è conservato integralmente in
[docs/history/aniyomi-changelog.md](docs/history/aniyomi-changelog.md).
La sua sezione “Unreleased” appartiene allo storico upstream importato:
non è una roadmap o una dichiarazione di release di Nyanime.
