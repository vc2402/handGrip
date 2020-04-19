
// required https://github.com/bogde/HX711 library
#include "HX711.h"
// required ArduinoBLE library
#include <ArduinoBLE.h>

// HX711 circuit wiring
const int LOADCELL_DOUT_PIN = 11;
const int LOADCELL_SCK_PIN = 12;

const int ledPin = LED_BUILTIN;

BLEService gripService("6015a815-6cf8-480e-8df2-908d9cf26dbc");

BLEStringCharacteristic commandCharacteristic("609ad4d6-d02b-4db5-bcc9-78265a1c4b40", BLERead | BLEWrite, 25);
BLELongCharacteristic pressureCharacteristic("f0b88329-41c5-43b8-8aa0-76e7566c8e58", BLERead | BLENotify);

HX711 scale;
float scaleValue = 243.f;

void setup() {
  Serial.begin(57600);
  while (!Serial);
  Serial.println("setup");
  pinMode(ledPin, OUTPUT); // use the LED as an output
  if (!BLE.begin()) {
    Serial.println("starting BLE failed!");

    while (1) {
      digitalWrite(ledPin, 1-digitalRead(ledPin));
      delay(200);
    }
  }
  BLE.setLocalName("SuperGripFishka");
  BLE.setAdvertisedService(gripService);
  gripService.addCharacteristic(pressureCharacteristic);
  gripService.addCharacteristic(commandCharacteristic);
  BLE.addService(gripService);
  pressureCharacteristic.writeValue(0);
  BLE.advertise();
  
  scale.begin(LOADCELL_DOUT_PIN, LOADCELL_SCK_PIN);
  scale.set_scale(scaleValue);
  scale.tare(5);
}

float units = 0.f;

void loop() {
  BLE.poll();
  if (commandCharacteristic.written()) {
    processCommand(commandCharacteristic.value());
  }
  
  if (scale.wait_ready_timeout(10)) {
    float u = scale.get_units();
    if(u != units) {
      Serial.print("reading:\t");
      Serial.println(u);
      pressureCharacteristic.writeValue(u*100);
      units = u;
    }
  } else {
    Serial.println("HX711 not found.");
  }

  delay(1000);
  
}

const char* TARE_COMMAND = "tare";
const char* WEIGHT_PREFIX = "weight: ";
const char* POWER_DOWN_COMMAND = "down";
const char* POWER_UP_COMMAND = "up";

void processCommand(String command) {
  Serial.println(command);
  if(command == TARE_COMMAND) {
    scale.tare(5);
  } else if(command.startsWith(WEIGHT_PREFIX)) {
    float weight = atof(command.substring(strlen(WEIGHT_PREFIX)).c_str());
    if(units != 0 && weight != 0) {
      Serial.print("current scale value: "); Serial.print(scaleValue);
      Serial.print("; current weight: "); Serial.print(units);
      Serial.print("; got weight: "); Serial.print(weight);
      scaleValue *= weight / units;
      Serial.print("; setting value: "); Serial.println(scaleValue);
      scale.set_scale(scaleValue);
    }
  } else if(command == POWER_DOWN_COMMAND) {
    scale.power_down();
  } else if(command == POWER_UP_COMMAND) {
    scale.power_up();
  }
}
