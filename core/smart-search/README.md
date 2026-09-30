# Motore di ricerca dei titoli

Modulo Android indipendente dai cataloghi HTTP e dalle API delle estensioni.
Contiene confronto Unicode, indice dinamico SymSpell, codec della cache e
sessioni con budget e paginazione separati. Nessun titolo o dizionario
precompilato è incluso nel modulo.

Le API `TitleMatcher`, `SearchCandidateProvider`, `ExtensionSearchAdapter` e
`SearchSession` sono descritte nella [guida completa](../../docs/smart-title-search.md).
La libreria filtra localmente tramite `LibraryTitleSearch`; i provider HTTP e
la persistenza Android sono nell'app. Non aggiungere qui regole per siti,
tracking, player o corrispondenze di identità tra fonti.

## Dipendenza e licenza

SymSpellKt 3.4.0 è una dipendenza Gradle con licenza MIT, senza modifiche.
Gli avvisi originali di Adam Brown, Lucky Sharma e Wolf Garbe sono conservati
nel [testo integrale distribuito nell'APK](../../app/src/main/assets/licenses/SymSpellKt-LICENSE.txt).
La [guida dei crediti](../../docs/credits.md) descrive la provenienza.

## Verifica

```powershell
./gradlew.bat :core:smart-search:testDebugUnitTest :core:smart-search:spotlessKotlinCheck
```

Test e benchmark versionati usano nomi sintetici. Il benchmark Android dedicato
è in `macrobenchmark` e non richiede emulatori o richieste a cataloghi reali.
