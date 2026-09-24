# Modalità TV Android: branch e integrazione

La modalità TV nativa vive nel branch `feature/android-tv`. `main` continua a
pubblicare la normale preview Android. Il progetto webOS rimane indipendente.

## Confini del codice

- `app/.../ui/tv/` contiene schermate, navigazione, stato dei profili, controlli
  da telecomando e ricevitore Companion. La UI legge solo i contratti generici
  delle Home fornite dalle estensioni.
- `TvModeResolver` e `TvUiMode` scelgono l'ingresso TV; `MainActivity` e le
  impostazioni aggiungono soltanto il punto di accesso e il selettore.
- `TvPlaybackAudience` passa al player i profili destinatari dei progressi. Il
  player e il motore video esistenti restano gli unici responsabili della
  riproduzione.
- Il modello di backup aggiunge un campo opzionale per i profili TV. I backup
  precedenti rimangono leggibili; file scaricati e PIN non sono esportati.

## Release separate

Il workflow `build_android_tv.yml` pubblica dal solo branch TV tag `tv-rNNNN` e
asset `Nyanime-TV-<architettura>-tv-rNNNN.apk` (più un APK universale). La
preview TV usa lo stesso ID applicazione e la stessa firma della preview
ordinaria: si può installare sopra di essa conservando i dati. La proprietà
Gradle `-Ptv-channel=true` indirizza soltanto quella build alle release TV.
L'updater normale continua a cercare solo tag `rNNNN` e APK ordinari.

Le novità della linea TV stanno in `TV_CHANGELOG.md`; ogni nuova release
richiede una nuova voce. Il workflow controlla formato, test, APK e firma prima
della pubblicazione.

## Portare il lavoro su main

Quando la modalità TV sarà pronta per tutti, integrare i commit del branch TV
in `main`, conservando i contratti generici e gli ingressi opzionali. Decidere
esplicitamente se mantenere due canali OTA oppure unificarli; non rinominare
tag già pubblicati e non cambiare la firma dell'APK. Prima della fusione,
verificare backup/ripristino, player normale, stanze, Cast, focus del telecomando
e almeno un dispositivo Android TV o display esterno reale. La prova sul solo
telefono stretto non copre queste ultime configurazioni.
