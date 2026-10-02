# Doppio tap posteriore

Funzione facoltativa in **Impostazioni → Gesti**, disattivata inizialmente.
Due tocchi sul retro del telefono eseguono un’azione diversa per navigazione,
player, lettore manga e telecomando TV. La schermata permette di scegliere
le azioni, provare il gesto senza eseguirle e calibrare con tre coppie di tocchi.
La sensibilità e il feedback tattile sono regolabili.

## Motore e integrazione

`BackTapDetector` è un rilevatore originale, senza dipendenze da modelli o codice
di altri progetti. Richiede accelerometro e giroscopio. Campiona a 100 Hz su un
thread dedicato, rimuove la gravità e cerca impulsi brevi sull’asse perpendicolare
allo schermo. Le due pulsazioni devono essere coerenti e distanti 120–500 ms;
rotazioni, impulsi prolungati, rumore laterale e vibrazioni ravvicinate sono scartati.
Una breve conferma di quiete precede la consegna; segue un secondo di cooldown.
Il comportamento non dipende dall’orientamento verticale/orizzontale del telefono.

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
fuori ordine. Verificano anche calibrazione e invalidazione delle consegne dopo un
cambio di proprietario. Questi test non misurano la precisione fisica sui telefoni.

La verifica manuale deve includere almeno 50 doppi tocchi per telefono e cover,
in verticale e orizzontale, riportando riconoscimenti, omissioni e attivazioni
accidentali. Obiettivo: almeno il 95% di riconoscimenti e risposta entro 200 ms dal
secondo tap. Provare inoltre digitazione, swipe, camminata, rotazioni, appoggio su
tavolo, altoparlanti ad alto volume e feedback aptico; poi passaggi tra player,
lettore, finestre, PiP e background. I criteri devono essere verificati su dispositivi
reali prima di dichiararli raggiunti; non sono una garanzia per ogni sensore/cover.
