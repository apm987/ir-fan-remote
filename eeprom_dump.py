#!/usr/bin/env python3
import time, glob, os, sys
import serial

def find_port():
    for p in ['/dev/ttyACM1', '/dev/ttyACM0']:
        if os.path.exists(p):
            return p
    ports = glob.glob('/dev/ttyACM*')
    return ports[0] if ports else None

def main():
    port = find_port()
    if not port:
        print('NO_PUERTO')
        return 1
    ser = serial.Serial(port, 115200, timeout=0.3)
    time.sleep(3.0)
    ser.reset_input_buffer()
    ser.write(b'd')
    lines = []
    t0 = time.time()
    buf = ''
    while time.time() - t0 < 8.0:
        data = ser.read(ser.in_waiting or 1)
        if data:
            buf += data.decode(errors='replace')
    for line in buf.splitlines():
        line = line.strip()
        if line.startswith('EEPROM,'):
            lines.append(line)
    ser.close()
    for line in lines:
        print(line)
    if not lines:
        print('SIN_EEPROM')
        return 2
    return 0

if __name__ == '__main__':
    sys.exit(main())
