# NFC Reader

App Android (Kotlin) che legge le carte NFC quando le avvicini al telefono: mostra i dati della carta e la sua memoria, in esadecimale, UTF-8 o ASCII. Sola lettura: non scrive mai sulla carta.

## Funzioni

- **Avvicina una carta**: l'app si apre (o torna in primo piano) e legge la carta.
- **App aperta**: le carte arrivano direttamente a lei, senza chiedere con quale app aprirle.
- **Dati della carta**: UID, tecnologie, ATQA/SAK, record NDEF (testo e URI decodificati), capacità.
- **Memoria**: letta per NTAG / MIFARE Ultralight (pagine fino a 1 KB) e MIFARE Classic (con le chiavi di fabbrica, settore per settore). Gli altri tipi mostrano solo i dati, non la memoria.
- **Formati**: Hex (con offset e colonna ASCII), UTF-8 o ASCII. Il testo si può selezionare e copiare.
- **Copia e scrittura** (solo NTAG e MIFARE Ultralight):
  - **Copia memoria** salva la memoria letta dentro l'app, anche dopo la chiusura.
  - **Scrivi la copia**: avvicina la carta di destinazione, controlla l'UID nella finestra di conferma e tocca **Scrivi**. L'app scrive le pagine utente dalla 4 in poi, poi le rilegge e confronta.
  - UID, pagine di lock e configurazione della carta di destinazione non vengono toccati. L'UID non viene copiato.
  - Non scrive su MIFARE Classic né su altri tipi di carta. Usala solo su carte tue o su cui hai il permesso di copiare i dati.
- **Senza NFC**: se il telefono non ha NFC, o è disattivato, l'app lo scrive sullo schermo.

## Requisiti

- Android 12 o superiore (`minSdk 31`), con NFC.
- JDK 17 o superiore. Testato con JDK 21.
- Android SDK con platform `android-35` e build-tools `35.0.0`.
- Variabile d'ambiente `ANDROID_HOME` impostata sulla cartella dell'SDK, di solito `%LOCALAPPDATA%\Android\Sdk` su Windows.

Non serve Android Studio: la build si fa da riga di comando con il Gradle wrapper incluso nel progetto.

## Come compilare

Da PowerShell o terminale, nella cartella del progetto:

```
gradlew.bat assembleDebug
```

L'APK viene creato in:

```
app\build\outputs\apk\debug\app-debug.apk
```

## Come installare sul telefono

1. Sul telefono attiva le opzioni sviluppatore: Impostazioni → Info sul telefono → Info software → tocca "Numero build" 7 volte.
2. In Opzioni sviluppatore attiva **Debug USB**.
3. Attiva l'NFC: Impostazioni → Connessioni → NFC.
4. Collega il telefono con il cavo USB e accetta la richiesta di debug sul display.
5. Controlla che il telefono sia riconosciuto:

```
adb devices
```

6. Installa l'APK:

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

7. Apri l'app con:

```
adb shell am start -n com.example.nfcreader/.MainActivity
```

Per provarla, tieni lo schermo acceso e sbloccato, poi avvicina una carta al retro del telefono.

## Struttura del progetto

```
app/src/main/java/com/example/nfcreader/
  MainActivity.kt          lettura della carta e schermo unico
app/src/main/res/          layout, icona, colori, stringhe, tema
```
