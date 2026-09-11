# Interfaccia Nyanime

Nyanime introduce una presentazione da catalogo video: fondo nero, superfici neutre,
accenti rossi, copertine grandi e pulsanti di riproduzione ben visibili. La Home
mantiene tutti i dati dichiarati dalle estensioni, ricerca, filtri, calendario,
paginazione, cronologia e aggiornamenti. Le schede compatte consentono di aprire
le informazioni complete. La ripresa conserva avanzamento, apertura della scheda,
elementi nascosti e ripristino.

Il nome e l'icona cambiano; identificativo Android, firma, archivi e integrazioni
rimangono compatibili. Il player e la sessione Cast mantengono i controlli esistenti.

## Manga

L'aggiornamento conserva il tema precedente per libreria, dettagli, esplorazione
e lettore manga. La preferenza dedicata in Aspetto permette di modificarlo.
La migrazione viene applicata una sola volta e preserva le scelte successive,
la modalità chiara/scura e l'impostazione AMOLED.

## Riferimento visivo

Il progetto di riferimento è [flutter_netflix](https://github.com/angjelkom/flutter_netflix)
di Jack (licenza MIT). La presentazione è implementata in Kotlin e Compose e usa
le immagini del catalogo o delle estensioni. Il marchio e l'icona dell'app sono Nyanime.

## Verifica locale delle anteprime

Le anteprime usano il renderer Compose sul computer, senza avviare emulatori:

```sh
./gradlew :app:updateDebugScreenshotTest
```

La variabile opzionale `NYANIME_PREVIEW_ARTWORK` può indicare una cartella locale
con `poster-0.jpg`, `poster-1.jpg` e `poster-2.jpg`. Le immagini vengono usate solo
dal codice di anteprima. In assenza di questi file vengono renderizzate copertine
colorate. I render locali non sono inclusi nell'app o nel repository.

I test `NyanimeThemeMigrationTest` verificano la conservazione delle preferenze
durante aggiornamento e successiva ricreazione dell'applicazione.
