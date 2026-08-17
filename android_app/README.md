# MANDO para Android

Aplicación Android nativa que emula el mando del extractor con los seis
códigos verificados. Usa el emisor infrarrojo integrado del teléfono mediante
`ConsumerIrManager`; no necesita Internet, cuenta de usuario ni permisos en
tiempo de ejecución.

## Requisito de hardware

El teléfono debe incorporar **bláster IR** y soportar una portadora de 38 kHz.
La cámara, el sensor de proximidad y NFC no pueden emitir estas señales. La
función `android.hardware.consumerir` se declara obligatoria, por lo que Google
Play filtra la app y no la ofrece a móviles sin emisor IR integrado.

## Botones incluidos

| Botón | Trama F12 | Código |
|---|---|---|
| Luz encendida | `110000001000` | `C08` |
| Luz apagada | `110000100000` | `C20` |
| Ventilador I | `110000000001` | `C01` |
| Ventilador II | `110000000100` | `C04` |
| Ventilador III | `110001000011` | `C43` |
| Ventilador apagado | `110000010000` | `C10` |

Cada toque transmite a 38 kHz los dos preámbulos fijos y seis repeticiones de
la trama de datos, reproduciendo el patrón validado en `../mando_v4.irplus`.

## Abrir y compilar

1. Instala Android Studio con Android SDK 36 y JDK 17 o posterior.
2. Abre la carpeta `android_app` como proyecto.
3. Espera a que termine la sincronización de Gradle.
4. Compila con **Build > Build APK(s)** o ejecuta `./gradlew assembleDebug`.

El APK de depuración queda en
`app/build/outputs/apk/debug/app-debug.apk`. Para instalarlo por ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La app requiere Android 5.0 (API 21) o posterior.

## Estructura relevante

- `app/src/main/java/.../IrProtocol.java`: genera la señal F12 en microsegundos.
- `app/src/main/java/.../RemoteCommand.java`: contiene los seis códigos.
- `app/src/main/java/.../MainActivity.java`: comprueba y usa el emisor IR.
- `app/src/test/.../IrProtocolTest.java`: valida tramas, tiempos y límite de 2 s.
- `artwork/icon-source.png`: ilustración fuente inspirada en el mando físico.
- `tools/GenerateLauncherIcons.java`: regenera las variantes del icono Android.

Referencias: [ConsumerIrManager](https://developer.android.com/reference/android/hardware/ConsumerIrManager)
y [`<uses-feature>`](https://developer.android.com/guide/topics/manifest/uses-feature-element).

El repositorio `irplus-remote/irplus-remote.github.io` se evaluó como posible
base, pero contiene la web y el conversor de irplus, no el código fuente de la
app Android. Por eso este proyecto implementa directamente la pequeña API
nativa necesaria en lugar de bifurcar una aplicación universal.
