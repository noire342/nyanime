# Dall'anime al manga

La scheda anime mostra i manga collegati attraverso gli ID di AniList. Se
l'adattamento parte da una novel, viene seguito anche il rapporto tra quella
novel e i suoi manga, distinguendolo dal collegamento diretto.

MangaBaka e MangaUpdates possono fornire punti documentati di inizio e fine
adattamento. Sono riferimenti editoriali, non una mappa completa di ogni episodio.
Gli intervalli stagione/episodio sono mostrati per aiutare a distinguere gli archi;
non vengono convertiti in capitoli stimati. Il salto a un capitolo viene proposto
solo con un checkpoint esplicito utilizzabile per l'episodio corrente. La presenza
di una stagione nel checkpoint richiede una conferma che questa versione non
deduce dal nome del titolo.

## Contratti delle estensioni

I contratti sono facoltativi e mantengono nell'estensione ogni regola del sito:

- `AnimeCatalogIdResolver.findAnimeByCatalogId(catalog, id)` risolve un ID anime.
- `RelatedMangaLinks.relatedMangaLinks(animeUrl)` fornisce collegamenti manga
  associati dal sito a una scheda anime.
- `MangaCatalogIdResolver.findMangaByCatalogId(catalog, id)` risolve un ID manga.
- `MangaCatalogLinkResolver.findMangaByCatalogLink(url)` accetta soltanto link
  appartenenti al catalogo che l'estensione gestisce.

Queste interfacce mantengono nome e firme nella build ottimizzata. Se un APK
include stub con gli stessi nomi, il caricamento usa le definizioni dell'app:
l'identità del contratto resta unica anche tra class loader distinti.

L'app passa un identificativo di catalogo, non costruisce URL del sito e non
interpreta il suo HTML. I dettagli restituiti devono contenere gli ID secondo il
[contratto dei metadati](extension-tracking-metadata.md). Nyanime controlla che
gli ID concordino con il manga collegato: un ID in conflitto impedisce
l'associazione automatica. Un titolo uguale non prova da solo l'identità.

Un'estensione può usare un indice locale di ID già incontrati quando il suo sito
non offre una ricerca per ID. Non equivale a una ricerca remota universale:
un titolo mai indicizzato può essere risolto soltanto se esiste un link diretto
accettato dall'estensione o un altro meccanismo di lookup. Se nessuna copia
verificata è disponibile, la scheda offre la ricerca per titolo come azione
esplicita di recupero; non sceglie automaticamente un risultato simile.

L'apertura di una copia non la aggiunge alla libreria e non modifica il progresso
anime. I servizi di catalogo ricevono ID; non ricevono cookie della fonte,
credenziali del tracker o URL di riproduzione.

Il risultato resta nello stato della scheda durante il passaggio al manga e il
ritorno. La richiesta segue il ciclo di vita della scheda, non quello della sua
composizione: non ricomincia a ogni ritorno e non sparisce durante un aggiornamento
del tracking o del punto raggiunto. Una risposta superata non sostituisce quella
relativa alla richiesta corrente; un errore conserva il risultato precedente.
