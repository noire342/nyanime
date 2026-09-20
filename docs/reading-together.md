# Leggi insieme

Video e manga usano **la stessa stanza**, lo stesso codice NY1, lo stesso invito QR,
gli stessi partecipanti (massimo otto) e le stesse connessioni cifrate Nostr.
Dal lettore, il pulsante con le persone apre la stanza. È disponibile anche da
Altro → Guarda e leggi insieme e dai controlli della stanza video.

## Leggere, raggiungere, tornare

Ogni persona mantiene il proprio manga, capitolo e pagina. Non esiste un comando
remoto che costringe il lettore a voltare pagina. La stanza mostra il punto degli
altri e distingue la lettura attiva dall'ultimo punto condiviso.

“Raggiungi questa pagina” apre l'esatto riferimento tramite l'estensione installata
e conserva il proprio punto. “Torna al mio punto” resta disponibile anche dopo
aver chiuso la stanza; il segnalibro è locale e sopravvive al riavvio dell'app.
Si può cambiare manga dalla libreria senza cambiare stanza. I nomi e i numeri
del database non vengono usati per scegliere silenziosamente un'altra edizione.
La fonte deve essere installata e attendibile, i riferimenti devono appartenere
alla sua origine e il numero di pagine deve corrispondere prima dello spostamento.
Download con una diversa suddivisione delle pagine richiedono la stessa edizione.

Aprire il lettore come ospite sospende il proprio video, senza mettere in pausa chi
continua a guardare. Chi legge è escluso dalla barriera di buffering video.
Se il creatore passa al manga, la selezione video comune viene sospesa. Tornare al
player riutilizza la stanza e i normali controlli di ripresa e sincronizzazione.
Il controller della lettura non contiene riferimenti al player.

## Schizzi condivisi

“Disegna” attiva una modalità esplicita; la lettura normale conserva i suoi gesti.
La barra offre cinque colori, tre spessori, annulla e pulizia della pagina.
Due dita permettono di tornare ai gesti di navigazione. Un tratto completato viene
trasmesso come coordinate, mai come immagine. Per disegnare sulla pagina dell'amico,
raggiungila: l'amico resta libero di leggere o cambiare pagina.

L'occhio mostra o nasconde tutti gli schizzi su questo telefono, senza cancellarli
per gli altri. Ogni partecipante può cancellare i propri; il creatore può pulire
l'intera pagina, con conferma. Le immagini originali e i download non vengono modificati.
Gli schizzi sono temporanei: fino a 12 tratti per pagina e 32 pagine recenti nella
memoria della stanza. La chiusura del creatore o la terminazione del suo processo
elimina questa memoria; non è un archivio di annotazioni.

Le coordinate sono normalizzate sulla pagina originale e trasformate per zoom,
lettura verticale, pagine divise, doppie pagine ruotate e impilate. Durante la stanza
il ritaglio dei bordi è sospeso, senza cambiare le preferenze salvate, per evitare
che due telefoni disegnino su rettangoli diversi.

## Protocollo e affidabilità

Il canale applicativo reading v1 è opzionale nel messaggio cifrato della stanza
[Guarda insieme](watch-together.md), senza nuovi segreti, relay o identità.
La capacità readingVersion viene negoziata prima che un ospite invii questi dati:
un host precedente non deve interpretare una presenza manga come stato video.
Entrambi i telefoni devono avere la nuova versione per leggere insieme.

Il creatore autentica e ammette i mittenti usando la stessa lista della stanza.
Pubblica la presenza aggregata e ordina le modifiche ai disegni. Ogni ospite ha
una sequenza di operazioni distinta dai messaggi video; ritenta una sola operazione
alla volta fino a ricevere conferma insieme allo stato della pagina. La coda è
limitata a 16 operazioni e resta visibile mentre la rete manca. I rifiuti per pagina
piena vengono confermati con un messaggio, senza bloccare le operazioni successive.

Revisioni e stati completi per pagina impediscono a messaggi duplicati o fuori ordine
di ripristinare un tratto cancellato. I cambi di presenza sono aggregati, gli stati
sono rinnovati e una connessione scaduta non viene mostrata come attiva.
Dimensioni UTF-8, coordinate, autori, numero di tratti e frequenza di ingresso sono
limitati prima di applicare un messaggio. I payload rimangono entro il limite
dell'involucro cifrato esistente.

Nessuna immagine, credenziale, cookie o URL risolto delle pagine viene trasmesso.
Titolo, capitolo, riferimenti di catalogo e pagina viaggiano solo nel canale cifrato
della stanza, non nella presenza sociale pubblica. La modalità incognito e le fonti
impostate come incognito non pubblicano la lettura né inviano schizzi.

## Verifica

ReadingRoomTest copre indipendenza dei lettori, cambio titolo, coda offline,
duplicazione, ordinamento, cancellazioni, autorizzazione, limiti e trasformazioni.
WatchRoomTest verifica il canale sulla medesima stanza, l'isolamento dal player,
la compatibilità con host precedenti e il ritorno al video.
ReadingRelayTest è una prova facoltativa su entrambi i relay, con due client reali
e soli dati sintetici, attivata da NYANIME_WATCH_NETWORK_TEST=1.

Le anteprime Compose coprono schermo stretto, testo grande, landscape, attesa ed
errori. I controlli sul PC non sostituiscono una prova dei gesti, dello scorrimento
e dell'apertura dei capitoli su due telefoni fisici. Non vengono usati emulatori.
