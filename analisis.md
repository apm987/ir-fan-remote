# Análisis y conclusiones de la señal IR

La entrada D2 detecta la señal eléctrica del ánodo del LED IR del mando. El
sketch filtra la portadora de aproximadamente 38 kHz y conserva la envolvente.

## Resumen ejecutivo

El protocolo ha sido decodificado por completo y validado: los códigos
generados para la configuración final de irplus funcionan contra el
dispositivo, un ventilador con luz y tres velocidades.

Cada pulsación del mando envía dos tramas de cabecera fijas seguidas de una
trama de datos repetida. Se ha identificado como una variante del protocolo
F12 compatible con la familia BA5104/SC5104: cada trama contiene 3 bits de
inicio, 2 bits de usuario y 7 bits de comando. Los seis botones están
identificados y validados, incluido VENT OFF (`0xC10`).

## Metodología

1. Captura de la envolvente con el Arduino Pro Micro (pin D2) mediante el
   comando `c` del sketch, que vuelca una línea RAW por el puerto serie.
2. Volcado de las firmas comprimidas de la EEPROM añadiendo el comando `d`.
3. Decodificación por software (`decodificar.py`, `decode_eeprom.py`).
4. Generación de códigos irplus (`generar_irplus.py`) y validación real.

Las herramientas reproducibles del proceso son `capturar.py`,
`eeprom_dump.py`, `decodificar.py`, `decode_eeprom.py` y
`generar_irplus.py`. Las capturas y registros de prueba se conservaron fuera
del contenido público del repositorio.

## Protocolo

### Portadora y unidad de tiempo

- Portadora: 38 kHz. La unidad de 420 us equivale a 16 ciclos de portadora
  (y 1260 us a 48 ciclos), lo que confirma el origen común de reloj.

### Codificación de bit F12

Cada bit ocupa una celda constante de ~1680 us (4 unidades) compuesta por una
marca y una pausa. El valor lo determina cuál de las dos mitades es larga:

| Bit | Marca | Pausa |
|-----|-------|-------|
| 1   | larga ~1250 us (3 u) | corta ~430 us (1 u) |
| 0   | corta ~410 us (1 u) | larga ~1270 us (3 u) |

Es una codificación por duraciones complementarias propia de F12, con una
unidad nominal de unos 422 us. No coincide con NEC, Sony ni RC5.

### Trama (12 bits)

Cada trama tiene 12 bits, es decir, 24 duraciones marca/pausa:

- Bits 1-3: inicio, siempre `110`.
- Bits 4-5: código de usuario, `00` en este mando.
- Bits 6-12: comando de 7 bits.

No hay un bit de paridad independiente. Los comandos observados tienen un
número impar de unos por la tabla de códigos del codificador, pero la primera
cabecera (`110000000000`) demuestra que no es una regla de paridad de trama.

La última celda comparte su pausa con la separación entre tramas. Por eso las
capturas muestran 7160 u 8000 us según el último bit:

| Último bit | Marca | Pausa/separación |
|--------|-------|-------|
| 0 | corta ~410 us | 8000 us |
| 1 | larga ~1250 us | 7160 us |

### Estructura de una pulsación

```
[Preámbulo 1] [Preámbulo 2] [Datos] [Datos] [Datos] ...
```

- Preámbulo 1 = `110000000000` (fijo para todos los botones).
- Preámbulo 2 = `110001111111` (fijo para todos los botones).
- Datos = trama del botón, repetida mientras se mantiene pulsado.

### Recuento de elementos (evidencia)

Toda captura cumple `elementos = 24n - 1`, donde `n` es el número de tramas
(la última trama queda sin su pausa final porque la captura termina en
silencio):

```text
2 tramas  ->  47 elementos (solo preámbulos)
4 tramas  ->  95
6 tramas  -> 143
8 tramas  -> 191
10 tramas -> 239
12 tramas -> 287
```

Esto explica por qué los botones parecían tener longitudes distintas: en
realidad dependía solo de cuánto tiempo se mantenía pulsado cada botón.

## Botones

| Botón | Función | Trama (12 bits) | Estado |
|-------|---------|-----------------|--------|
| 1 | LUZ ON | `110000001000` | verificado |
| 2 | LUZ OFF | `110000100000` | verificado |
| 3 | VENT I | `110000000001` | verificado |
| 4 | VENT II | `110000000100` | verificado |
| 5 | VENT III | `110001000011` | verificado |
| 6 | VENT OFF | `110000010000` | verificado |

Los comandos de 7 bits (bits 6-12):

```text
LUZ ON   = 0001000 (0x08)
LUZ OFF  = 0100000 (0x20)
VENT I   = 0000001 (0x01)
VENT II  = 0000100 (0x04)
VENT III = 1000011 (0x43)
VENT OFF = 0010000 (0x10)
```

Estos valores pertenecen a la tabla característica de ocho teclas de los
codificadores BA5104/SC5104: `01`, `02`, `04`, `08`, `10`, `20`, `43` y `46`.

## Integración con irplus

Formato usado: `WINLIRC_RAW` con duraciones en microsegundos.

Detalles relevantes descubiertos durante la integración:

- `format="WINLIRC_RAW"` acepta las duraciones marca/pausa directamente,
  empezando por marca.
- El atributo `frequency="38000"` fija la portadora.
- El código debe terminar en un **hueco final** (espacio largo, ~40 ms), no
  en una marca; los ejemplos de referencia que funcionan lo hacen así.
- Se envían 6 tramas de datos por pulsación (preámbulo + 6 datos, ~216 ms).

Valores nominales usados en la generación:

```text
marca corta 410 us, marca larga 1250 us
pausa corta 430 us, pausa larga 1270 us
bit12 pausa 8000 us (bit 0) / 7160 us (bit 1)
hueco final 40000 us
```

## Archivos

- `arduino_ir.ino`: captura de envolvente + comando `d` de volcado EEPROM.
- `mando_v4.irplus`: configuración final limpia con los seis botones
  verificados.
- `generar_irplus.py`: regenera el archivo con auto-versionado (v1, v2, ...).
- `analisis.md`: este documento.
- `android_app/`: aplicación Android nativa con los seis botones y generación
  programática de las tramas F12 para `ConsumerIrManager`.

## Conclusiones

1. El mando usa una variante F12 compatible con BA5104/SC5104, unidad de unos
   422 us, portadora de 38 kHz y comandos de 7 bits.
2. Los seis botones están identificados, reproducidos y validados en irplus.
3. VENT OFF es `110000010000` (`0xC10`).

## Siguientes pasos

1. Probar la aplicación de `android_app/` en un teléfono con bláster IR.
2. Crear un modo de reproducción con LED IR y transistor en el
   Arduino (no alimentar el LED directamente desde el pin).
