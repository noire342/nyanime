# AniSkip

L'integrazione era già presente nel player. Si attiva cercando AniSkip nelle
impostazioni, oppure in Player interno > Salto dell'introduzione > Attiva AniSkip.
Non serve più collegare manualmente il titolo a un tracker: l'app usa gli ID
forniti dall'estensione, un tracker già collegato oppure una ricerca univoca
nel catalogo. In caso di titolo o stagione ambigui non indovina.
I salti manuali, automatici e con conto alla rovescia mantengono le preferenze
esistenti.

Le richieste usano l'[API ufficiale v2](https://api.aniskip.com/api-docs), conservano
i numeri di episodio frazionari e riconoscono opening, ending, mixed opening,
mixed ending e recap. Le risposte senza intervalli sono normali; i segmenti
sconosciuti, invertiti o fuori dalla durata del video vengono ignorati.

Il client HTTP è condiviso, ha timeout limitati e annulla la chiamata quando
l'attività o il caricamento corrente vengono annullati. L'intera ricerca ha
un limite di quindici secondi. Le risposte vengono applicate solo all'episodio
e al video che le hanno richieste. Un tracker non supportato non impedisce
di usare un'associazione MAL o AniList disponibile.

Il lavoro precedente era già eseguito in background: queste correzioni non
costituiscono una diagnosi del buffering segnalato sul dispositivo.
