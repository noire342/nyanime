# Protezione laterale

La protezione laterale è facoltativa e richiede un pannello hardware compatibile. Non simula l’effetto con filtri, sfocature o oscuramenti software. La protezione delle catture e la modalità incognito rimangono indipendenti.

## Impostazioni e dati

In **Impostazioni → Sicurezza → Protezione laterale** il comando principale è inizialmente spento. Video, lettore manga, cronologia, ripresa e ricerca sono selezionati; l’opzione aggiuntiva NSFW è inizialmente spenta. Quest’ultima usa il flag pubblico dell’estensione oppure etichette esplicite del contenuto; non riconosce titoli, domini o immagini per tentativi. Contenuti non segnalati non vengono classificati automaticamente.

Il comando in **Player → Altro** è un’eccezione per quella riproduzione e non modifica la preferenza generale. Un errore non interrompe video o lettura; dopo una precedente applicazione riuscita viene mostrato un unico avviso per installazione. Preferenze portabili nei backup, disponibilità e avviso locali. Display esterni, PiP, multifinestra e finestre aggiuntive non sono attualmente validati.

## Modulo e rimozione

`core:privacy-display` dipende soltanto dal framework Android. `AndroidPrivacyDisplayBackends` compone gli adattatori senza riferimenti Samsung nelle schermate o nella politica dell’app. Il contratto `PrivacyDisplayBackend` isola il produttore; `PrivacyDisplaySession` serializza applicazione e pulizia e rilascia il target; `PrivacyRegion` e `PrivacyViewGeometry` gestiscono le coordinate. L’implementazione Samsung usa tre signature non pubbliche tramite reflection ordinaria, senza bypass delle restrizioni.

La politica immutabile `PrivacyDisplayPolicy` e le preferenze appartengono a `ui/privacy`, i controlli e i modifier a `presentation/privacy`. Le schermate dichiarano aree; non conoscono Samsung. Non vengono aggiunte chiamate mpv, decoder, servizi, permessi overlay o impostazioni globali del dispositivo.

L’API Samsung di posizionamento modifica il RenderNode della View destinataria: non deve essere chiamata sul decor o su una View che disegna contenuti. `AndroidPrivacyDisplayTarget` possiede una View trasparente e non interattiva nel `ViewGroupOverlay` della finestra, separata dalla misura e dal layout dei contenuti. Solo quella View viene posizionata e attivata; gli aggiornamenti della posizione non ripetono l’attivazione. Disattivazione e chiusura rimuovono l’ancora e tutti i riferimenti alla finestra.

Nella prova SM-S948B con firmware `S948BXXS4AZHL`, i log del driver hanno mostrato un’espansione di un pixel a sinistra/in alto e due a destra/in basso. Una prova strumentale separata ha isolato il vincolo del bordo: rettangoli piccoli sono accettati anche con raggio zero; un rettangolo ampio con margine richiesto di 13 pixel è rifiutato, mentre 14, 15, 16, 20 e 24 pixel sono accettati. L’adattatore compensa l’espansione e riserva 16 pixel interni **dopo** di essa per includere l’arrotondamento di un pixel osservato durante le transizioni. La fascia esterna imposta dal firmware non viene dichiarata protetta. `PrivacyViewPlacement` gestisce separatamente rettangolo nel display e coordinate locali dell’ancora, usando le dimensioni correnti anche dopo rotazione. Una regione completamente esclusa dal ritaglio sospende la protezione senza dichiarare un guasto hardware; la sessione espone la regione richiesta all’API e deduplica quella originaria, evitando riapplicazioni continue quando i bordi vengono corretti. Questa evidenza tecnica non costituisce una verifica ottica.

Con la funzione disabilitata o non disponibile, i modifier non registrano aree né callback geometrici e il controller non installa listener di disegno o viste aggiuntive. Il player conserva soltanto una dichiarazione del viewport, senza controlli per frame. Rete, renderer e decoder restano fuori dal modulo.

`PrivacyHardwareProbe` in `core:privacy-display/src/androidTest` è uno strumento manuale senza icona e senza permessi overlay, separato dall’APK distribuito. Confronta regioni e raggi nel solo viewport di prova. Esempio: `am instrument -w -e margins 13,14,15,16 nyanime.privacy.display.test/nyanime.privacy.display.PrivacyHardwareProbe`. Correlare i casi con i log del driver, verificare lateralmente e rimuovere il pacchetto di test al termine.

Per rimuovere la funzione: eliminare le dichiarazioni `privacyRegion`/`nsfwPrivacy`/`nsfwSourcePrivacy`, le registrazioni native, il comando in Altro e il gruppo Sicurezza; rimuovere controller e registrazioni DI; infine eliminare modulo, dipendenza Gradle e risorse. Nessun formato di libreria, database, estensione o backup richiede migrazione. Le preferenze inutilizzate possono essere eliminate per prefisso, senza modificare altri dati.

## Validazione e rilascio

Prima compatibilità hardware: Samsung Galaxy S26 Ultra (`SM-S948…`), Android 16+, display principale e tutte le signature accessibili. La presenza delle API e il successo di una chiamata **non provano** l’effetto ottico. Il parametro float non è un’intensità: la prova deve verificare coordinate e geometria degli angoli.

L’elenco delle combinazioni modello/firmware/aree fisicamente verificate è inizialmente vuoto. Le build ordinarie e CI non abilitano modalità prive di evidenza. Per preparare **soltanto un APK locale di verifica**, usare `assemblePreview -PprivacyDisplayValidation=true`: l’interfaccia segnala che si tratta di una prova. Il flag non supera il controllo hardware e non abilita PiP o multifinestra. Solo questa build include “Copia dati della prova” nella sezione Sicurezza: copia modello, fingerprint, revisione e disponibilità, senza identificativi personali o dati dei contenuti.

Prima di aggiungere una combinazione verificata, registrare modello, fingerprint del firmware, aree provate e risultato di: visione frontale/laterale a diverse luminosità, video con sottotitoli nelle bande, zoom e scorrimento manga, Home e ricerca, transizioni, rotazione, ritorno da PiP, interazione con Privacy Display di sistema e prestazioni. Nessuna combinazione è dichiarata verificata da screenshot o ADB.

Implementazione originale: nessun codice, asset, gestione degli stati o test del progetto PrivacyBox viene incorporato. Riferimenti tecnici: [API non pubbliche Android](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces), [hardware Samsung](https://www.samsung.com/uk/support/mobile-devices/how-to-use-privacy-display-on-the-samsung-galaxy-s26-ultra/), [distinzione di licenza del progetto di riferimento](https://github.com/moni11811/PrivacyBox-S26-Ultra/blob/main/LICENSING.md).
