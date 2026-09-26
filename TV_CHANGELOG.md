# Nyanime TV

Questo changelog riguarda la modalità TV sperimentale del branch `feature/android-tv`.
Le release `tv-rNNNN` sono separate dalle release Android ordinarie `rNNNN`.

## Episodi e Home aggiornati

- Le schede anime verificano in silenzio nuovi episodi anche quando ne mostrano già
  alcuni, mantenendo l'elenco locale mentre la fonte risponde.
- Le Home online riconvalidano le sezioni mentre restano aperte; la Home manga
  verifica i dati quando vi si ritorna.
- Entrando nella Home anime, i titoli seguiti vengono aggiornati quando dovuto,
  così «Continua a guardare» e «Le tue novità» possono mostrare i nuovi episodi.

## Riproduzione in finestra e tracking

- Il player Android continua a riprodurre durante l'ingresso in picture-in-picture
  e non lascia il video spostato in una metà della finestra.
- Nelle impostazioni Tracking è possibile riesaminare manualmente i titoli
  iniziati e non ancora collegati, anche dopo il recupero iniziale.

## Novità nella Home e aggiornamenti Android

- L'indicatore delle novità nelle Home anime e manga usa il colore delle altre
  icone, compare solo quando ci sono nuovi elementi e porta alla relativa sezione.
- Un aggiornamento dell'app già scaricato resta installabile da Altro anche dopo
  aver lasciato la schermata di download.
- Il passaggio con il dito tra Anime e Manga nella schermata Novità è continuo;
  i filtri e lo stato vuoto mostrano più chiaramente i contenuti disponibili.

## Collegamento automatico ai tracker

- Quando si avvia un video o si legge una pagina, i tracker già configurati
  vengono collegati in background se il titolo è identificato senza ambiguità.
- Le estensioni possono fornire gli ID di catalogo per distinguere stagioni
  ed edizioni. AniSkip può usarli senza un'associazione manuale preventiva.

## Novità personali e aggiornamenti

- Le novità dei titoli seguiti, guardati o letti sono disponibili anche
  nell'interfaccia Android accessibile dalla modalità TV. L'elenco raggruppa
  episodi e capitoli per titolo e si può aprire dalla campanella.
- Il calendario permette di scegliere anche i giorni senza uscite e segnala
  chiaramente quando non è previsto nulla.
- La schermata Info può scaricare l'aggiornamento nell'app e proporre
  l'installazione con avanzamento visibile. L'opzione è attiva inizialmente
  e rispetta le scelte già salvate.

## Prima versione della modalità TV Android

- Interfaccia TV nativa con Home per fonte, ricerca, categorie, sezioni, schede titolo e selezione episodi, mantenendo i contenuti nei contratti generici delle estensioni.
- Navigazione da telecomando con scorciatoie contestuali, colori coerenti, legenda temporanea e pulsanti adattati agli schermi stretti.
- Fino a cinque profili familiari, Anonimo e Team Watch locale; progressi separati e backup dei dati dei profili senza PIN.
- Player Android esistente, stanze e ricevitore Companion accessibili dalla modalità TV; opzione per tornare alla normale interfaccia.
- Tasto Indietro corretto: ogni pressione torna di una sola schermata e la scelta dei profili resta aperta.
- La scorciatoia gialla raggiunge la prima sezione senza crash e lascia visibile il suo titolo sotto l'intestazione; il pulsante Stanze non mostra più un colore privo di scorciatoia.
