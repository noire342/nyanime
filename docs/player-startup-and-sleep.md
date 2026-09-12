# Avvio Smart e timer di sospensione

In Impostazioni > Player interno > Anime4K, **Avvia Smart automaticamente**
è attivo per impostazione predefinita. Disattivandolo, Smart non si avvia
all'apertura dei video, anche se in passato era stato memorizzato per
quell'episodio. Il pulsante SM rimane disponibile. Una scelta manuale nella
sessione corrente resiste ai cambi di qualità e ai ricaricamenti dello stream.
Le scelte Off, Massimo e Personalizzato già salvate restano valide.
L'algoritmo, la calibrazione e gli shader Smart non cambiano.

Il timer già presente in **Altro > Timer di sospensione** mantiene la finestra
e il comportamento originali: alla scadenza mette in pausa e mostra il messaggio.
È corretto soltanto il conteggio, che ora usa una scadenza monotona anziché
accumulare attese di un secondo; non aspetta un ulteriore secondo dopo lo zero.
L'annullamento e la sostituzione del timer mantengono il comportamento precedente.
Non sono aggiunti nuovi permessi o comandi per spegnere lo schermo.
