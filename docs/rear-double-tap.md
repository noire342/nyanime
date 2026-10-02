# Doppio tap posteriore

Funzione facoltativa in **Impostazioni → Gesti**, disattivata inizialmente.
Due tocchi sul retro del telefono eseguono un’azione diversa per navigazione,
player, lettore manga e telecomando TV. La schermata permette di scegliere
le azioni, provare il gesto senza eseguirle e calibrare con tre coppie di tocchi.
La sensibilità e il feedback tattile sono regolabili.

## Motore e integrazione

`BackTapDetector` è un rilevatore originale, senza dipendenze da modelli o codice
di altri progetti. Richiede accelerometro e giroscopio. Richiede fino a 400 Hz su un
thread dedicato, rispettando il sensore più lento e ripiegando su 200 Hz se Android
non consente la frequenza maggiore. La frequenza effettiva dipende dal dispositivo;
il controllo privacy del microfono può limitarla senza impedire il funzionamento.
Rimuove la gravità e cerca impulsi brevi sull’asse perpendicolare
allo schermo. Le due pulsazioni devono essere distanti 120–550 ms. La rotazione
viene filtrata e deve essere sostenuta sia nel segnale istantaneo sia in quello
filtrato: la coda del filtro dopo un tocco deciso non prolunga il blocco.
La direzione viene valutata sul picco, senza annullare l'impulso per il contraccolpo
laterale successivo. L'intensità non ha un tetto massimo: gli urti singoli vengono
esclusi per assenza di un secondo impulso comparabile. Ogni impulso viene osservato per almeno 80 ms, includendo
i rimbalzi di polarità opposta, e la quiete dipende dalla sua ampiezza anziché
da una soglia assoluta troppo stretta. Impulsi prolungati, movimenti laterali,
terzi tocchi e vibrazioni ravvicinate sono scartati. La consegna attende almeno
110 ms dall'ultimo picco; segue un secondo di cooldown.
Il comportamento non dipende dall’orientamento verticale/orizzontale del telefono.

La calibrazione misura prima il movimento di fondo per un secondo e riconosce
anche coppie di tocchi leggere, senza applicare la soglia iniziale dell’uso normale.
Tre coppie valide determinano la sensibilità personale mediante la mediana delle
intensità; il filtro mantiene un limite adattato al rumore. L’aggiornamento del
rumore dipende dal tempo trascorso, senza cambiare sensibilità al cambiare della
frequenza dei sensori. Durante la calibrazione nessuna azione viene eseguita.
È necessario usare i tocchi che si vogliono riprodurre quotidianamente: una
calibrazione fatta con urti molto forti non insegna la sensibilità ai tocchi lievi.

`BackTapCoordinator` assegna i sensori a una sola Activity ripresa e con il focus.
Disabilitazione, background, PiP, finestre sovrapposte e comandi bloccati fermano
l’ascolto; il contatto con lo schermo invalida i gesti e richiede una nuova baseline.
La consegna controlla proprietario, generazione, età e disponibilità dell’azione sul
thread principale. Il worker non invoca mai il player nativo.

Player e Cast riutilizzano i comandi esistenti. Play/pausa e seek del player
mantengono quindi la sincronizzazione delle stanze. Nel lettore si usa l’ordine
di lettura del viewer, inclusi i manga da destra a sinistra e lo scorrimento webtoon.

Azioni, abilitazione e sensibilità sono portabili nei backup. La soglia calibrata è
stato locale del dispositivo e non viene esportata. Non si salvano né trasmettono
campioni dei sensori. Non servono root, accessibilità o servizi permanenti.

## Verifica

I test automatici riproducono tracce sintetiche con timestamp, doppio/singolo tap,
rumore, vibrazioni, rotazione, impatti, assenza di gyro, contatto, cooldown e campioni
fuori ordine. Quattro registrazioni di gesti reali, ridotte ai soli valori dei sensori
e timestamp relativi, verificano oscillazione della mano e rimbalzi sia con la soglia
di calibrazione sia con quella iniziale. Verificano anche invalidazione delle consegne dopo un
cambio di proprietario. Questi test non misurano la precisione fisica sui telefoni.
Le prove sintetiche includono tocchi deboli, impulsi brevi tra due campioni a 100 Hz
e rumore/vibrazioni con la soglia calibrata, a 100, 200 e 400 Hz. La registrazione
del rumore sul dispositivo e la frequenza richiesta non dimostrano il tasso di
riconoscimento dei tocchi reali.

La verifica manuale deve includere almeno 50 doppi tocchi per telefono e cover,
in verticale e orizzontale, riportando riconoscimenti, omissioni e attivazioni
accidentali. Obiettivo: almeno il 95% di riconoscimenti e risposta entro 200 ms dal
secondo tap. Provare inoltre digitazione, swipe, camminata, rotazioni, appoggio su
tavolo, altoparlanti ad alto volume e feedback aptico; poi passaggi tra player,
lettore, finestre, PiP e background. I criteri devono essere verificati su dispositivi
reali prima di dichiararli raggiunti; non sono una garanzia per ogni sensore/cover.
