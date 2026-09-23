# Traduzione manga offline

La modalità opzionale si abilita in **Impostazioni → Lettore → Traduzione manga offline**. Nel lettore, le azioni della pagina aprono un'anteprima indipendente dall'immagine originale. Il download dei modelli avviene solo dopo il comando esplicito dell'utente. Il pacchetto testo occupa circa 615 MB; i tre piccoli modelli OCR vengono scaricati solo per la lingua scelta. È possibile eliminare i modelli dall'anteprima per recuperare spazio.

La pipeline legge la pagina attraverso l'interfaccia generica del lettore, riduce le immagini molto grandi, calcola una chiave privata SHA-256, riconosce le righe con Tesseract e le raggruppa prima della traduzione. SMaLL-100 ONNX traduce localmente dal giapponese o dall'inglese all'italiano. Sono disponibili l'immagine originale, un overlay e una ricostruzione con inpainting Telea. Le correzioni esatte possono essere salvate nel glossario personale. I risultati intermedi sono salvati nella cache dopo ogni regione, così una pagina o un capitolo interrotto possono essere riavviati senza tradurre di nuovo tutto.

L'anteprima non riscrive download o file originali. L'elaborazione di un capitolo è legata alla sessione del lettore: per ora, uscendo dal lettore, si interrompe e alla riapertura riprende dai risultati in cache. Non esiste ancora un servizio persistente che continui il capitolo dopo la chiusura del lettore. La ricostruzione classica funziona meglio su balloon a sfondo uniforme; su tavole complesse l'overlay è spesso più leggibile. I risultati OCR e le traduzioni automatiche richiedono sempre revisione, soprattutto con testo verticale, font decorativi e onomatopee.

I file dei modelli sono versionati e verificati tramite dimensione e SHA-256 prima di essere usati. Non sono inclusi nell'APK. I download interrotti del pacchetto testo vengono ripresi con HTTP Range quando il server lo supporta. La cache contiene OCR e testo derivato, senza URL delle fonti o immagini originali.

- SMaLL-100 ONNX: [casawolice/small100-onnx](https://huggingface.co/casawolice/small100-onnx), licenza MIT, revisione `5c2c73ac70bee9c58f5a7ac5e84a36bee25db8ee`.
- Dati OCR: [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast), licenza Apache-2.0, revisione `87416418657359cb625c412a48b6e1d6d41c29bd`.
- Runtime: [ONNX Runtime](https://onnxruntime.ai/docs/tutorials/mobile/), [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android), [OpenCV](https://opencv.org/opencv4android-usage-models/) e [DJL Tokenizers](https://docs.djl.ai/master/docs/huggingface_tokenizers.html).

Questa pipeline è indipendente dall'elaborazione video Ultra e non ne riutilizza job, vincoli termici o stato.
