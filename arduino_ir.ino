// Captura la envolvente de la señal IR tomada del ánodo del LED.
// Filtra por software la portadora de aproximadamente 38 kHz.
// Entrada: D2. Comandos USB: c = capturar, r = registrar 6 botones.

#include <EEPROM.h>

const uint8_t INPUT_PIN = 2;
const uint16_t MAX_TIMES = 300;
const uint16_t CARRIER_GAP_US = 100;
const uint32_t FRAME_END_US = 12000UL;
const uint8_t BUTTONS = 6;
const uint16_t PACKED_BYTES = (MAX_TIMES + 3) / 4;
const uint16_t SLOT_BYTES = 2 + PACKED_BYTES;

volatile uint16_t timesUs[MAX_TIMES];
volatile uint16_t timeCount = 0;
volatile uint32_t lastEdgeUs = 0;
volatile uint32_t burstStartUs = 0;
volatile bool armed = false;
volatile bool capturing = false;
volatile bool inBurst = false;
volatile bool frameReady = false;
volatile bool overflowed = false;

uint8_t registrationSlot = 0;
bool registrationActive = false;

uint8_t durationClass(uint16_t value) {
  if (value > 5000) return 2;
  if (value > 700) return 1;
  return 0;
}

uint16_t slotAddress(uint8_t slot) {
  return 2 + (uint16_t)slot * SLOT_BYTES;
}

void dumpEEPROM() {
  for (uint8_t slot = 0; slot < BUTTONS; ++slot) {
    const uint16_t address = slotAddress(slot);
    const uint16_t count = (uint16_t)EEPROM.read(address) |
                           ((uint16_t)EEPROM.read(address + 1) << 8);
    Serial.print(F("EEPROM,"));
    Serial.print(slot + 1);
    Serial.print(',');
    Serial.print(count);
    Serial.print(',');
    for (uint16_t i = 0; i < count; ++i) {
      const uint8_t packed = EEPROM.read(address + 2 + (i / 4));
      const uint8_t cls = (packed >> ((i % 4) * 2)) & 0x03;
      Serial.print(cls == 2 ? 'G' : (cls == 1 ? 'L' : 'S'));
    }
    Serial.println();
  }
}

void saveButton(uint8_t slot, uint16_t count) {
  const uint16_t address = slotAddress(slot);
  EEPROM.update(address, count & 0xff);
  EEPROM.update(address + 1, count >> 8);

  for (uint16_t i = 0; i < PACKED_BYTES; ++i) {
    uint8_t packed = 0;
    for (uint8_t part = 0; part < 4; ++part) {
      const uint16_t index = i * 4 + part;
      if (index < count) {
        packed |= durationClass(timesUs[index]) << (part * 2);
      }
    }
    EEPROM.update(address + 2 + i, packed);
  }
}

void armCapture() {
  noInterrupts();
  timeCount = 0;
  overflowed = false;
  frameReady = false;
  capturing = false;
  inBurst = false;
  armed = true;
  interrupts();
}

void addTime(uint32_t value) {
  if (timeCount < MAX_TIMES) {
    timesUs[timeCount++] = value > 65535UL ? 65535 : (uint16_t)value;
  } else {
    overflowed = true;
    capturing = false;
    frameReady = true;
  }
}

void onEdge() {
  const uint32_t now = micros();

  if (armed) {
    lastEdgeUs = now;
    burstStartUs = now;
    armed = false;
    capturing = true;
    inBurst = true;
    timeCount = 0;
    return;
  }

  if (!capturing) return;

  const uint32_t delta = now - lastEdgeUs;

  // Los intervalos cortos son la portadora. Un intervalo largo marca
  // el final de una ráfaga y el comienzo de la pausa siguiente.
  if (delta > CARRIER_GAP_US && inBurst) {
    addTime(lastEdgeUs - burstStartUs);
    addTime(delta);
    burstStartUs = now;
  }

  lastEdgeUs = now;
}

void setup() {
  // No activar INPUT_PULLUP: la señal procede del mando.
  pinMode(INPUT_PIN, INPUT);
  attachInterrupt(digitalPinToInterrupt(INPUT_PIN), onEdge, CHANGE);

  Serial.begin(115200);
  unsigned long start = millis();
  while (!Serial && millis() - start < 3000) {}

  Serial.println(F("Capturador IR RAW en D2"));
  Serial.println(F("c: capturar; r: registrar 6 botones; ?: ayuda"));
}

void loop() {
  if (Serial.available()) {
    const char command = Serial.read();
    if (command == 'c' || command == 'C') {
      registrationActive = false;
      armCapture();
      Serial.println(F("ARMADO"));
    } else if (command == 'r' || command == 'R') {
      registrationSlot = 0;
      registrationActive = true;
      armCapture();
      Serial.println(F("REGISTRO INICIADO: PULSA BOTON 1"));
    } else if (command == '5' || command == '6') {
      registrationSlot = command - '1';
      registrationActive = true;
      armCapture();
      Serial.print(F("REPETICION: PULSA BOTON "));
      Serial.println(command);
    } else if (command == 'd' || command == 'D') {
      dumpEEPROM();
    } else if (command == '?') {
      Serial.println(F("c: captura individual"));
      Serial.println(F("r: registra seis botones en EEPROM"));
      Serial.println(F("d: vuelca las firmas de EEPROM"));
    }
  }

  // El final de una trama se detecta por ausencia de flancos durante 12 ms.
  noInterrupts();
  if (capturing && micros() - lastEdgeUs > FRAME_END_US) {
    if (inBurst) {
      addTime(lastEdgeUs - burstStartUs);
    }
    capturing = false;
    inBurst = false;
    frameReady = true;
  }
  const bool ready = frameReady;
  interrupts();

  if (ready) {
    noInterrupts();
    const uint16_t count = timeCount;
    const bool overflow = overflowed;
    frameReady = false;
    interrupts();

    if (registrationActive && !overflow) {
      saveButton(registrationSlot, count);
      Serial.print(F("BOTON REGISTRADO: "));
      Serial.print(registrationSlot + 1);
      Serial.print(F("/6, elementos="));
      Serial.println(count);

      ++registrationSlot;
      if (registrationSlot < BUTTONS) {
        armCapture();
        Serial.print(F("PULSA BOTON "));
        Serial.println(registrationSlot + 1);
      } else {
        registrationActive = false;
        Serial.println(F("REGISTRO COMPLETADO"));
      }
    } else {
      Serial.print(F("RAW,"));
      Serial.print(count);
      if (overflow) Serial.print(F(",OVERFLOW"));
      Serial.println();

      noInterrupts();
      for (uint16_t i = 0; i < count; ++i) {
        Serial.print(timesUs[i]);
        if (i + 1 < count) Serial.print(',');
      }
      interrupts();
      Serial.println();
      Serial.println(F("FIN"));
    }
  }
}
