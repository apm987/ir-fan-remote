#!/usr/bin/env python3
import sys, time, glob, os
import serial

def find_port():
    for p in ['/dev/ttyACM1', '/dev/ttyACM0']:
        if os.path.exists(p):
            return p
    ports = glob.glob('/dev/ttyACM*')
    return ports[0] if ports else None

def main():
    boton = sys.argv[1]
    intento = sys.argv[2] if len(sys.argv) > 2 else '01'
    base = os.path.dirname(os.path.abspath(__file__))
    outdir = os.path.join(base, 'capturas')
    os.makedirs(outdir, exist_ok=True)

    port = find_port()
    if not port:
        print('NO_PUERTO')
        return 1

    ser = serial.Serial(port, 115200, timeout=0.2)
    time.sleep(3.0)  # esperar reset del Leonardo
    ser.reset_input_buffer()

    armed = False
    for _ in range(5):
        ser.write(b'c')
        t0 = time.time()
        while time.time() - t0 < 2.0:
            line = ser.readline().decode(errors='replace').strip()
            if 'ARMADO' in line:
                armed = True
                break
        if armed:
            break
    if not armed:
        print('NO_ARMADO')
        ser.close()
        return 1

    print('ARMADO', flush=True)

    raw_line = None
    count = None
    overflow = False
    buf = ''
    t0 = time.time()
    done = False
    while time.time() - t0 < 90.0:
        try:
            data = ser.read(ser.in_waiting or 1)
        except serial.SerialException:
            break
        if not data:
            continue
        buf += data.decode(errors='replace')
        while '\n' in buf:
            line, buf = buf.split('\n', 1)
            line = line.strip()
            if line.startswith('RAW,'):
                count = line.split(',')[1]
                overflow = 'OVERFLOW' in line
            elif line == 'FIN':
                done = True
                break
            elif line and line[0].isdigit() and ',' in line:
                raw_line = line
        if done:
            break

    ser.close()

    if raw_line is None:
        print('SIN_DATOS count=%s' % count, flush=True)
        return 2

    outfile = os.path.join(outdir, f'boton{boton}_{intento}.raw')
    with open(outfile, 'w') as f:
        f.write(raw_line + '\n')
    n = raw_line.count(',') + 1
    print(f'GUARDADO {outfile} count={count} overflow={overflow} elementos={n}', flush=True)
    return 0

if __name__ == '__main__':
    sys.exit(main())
