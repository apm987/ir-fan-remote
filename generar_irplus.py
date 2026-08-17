#!/usr/bin/env python3
import os

MARK_SHORT = 410
MARK_LONG = 1250
SPACE_SHORT = 430
SPACE_LONG = 1270
BIT12_SPACE_0 = 8000    # bit 12 = 0 -> marca corta + espacio 8000 us
BIT12_SPACE_1 = 7160    # bit 12 = 1 -> marca larga + espacio 7160 us
TRAILING_GAP = 40000    # hueco final de fin de pulsacion

PREAMBLE1 = '110000000000'
PREAMBLE2 = '110001111111'

# label, codigo de trama (12 bits)
BUTTONS = [
    ('LUZ ON',   '110000001000'),
    ('LUZ OFF',  '110000100000'),
    ('VENT I',   '110000000001'),
    ('VENT II',  '110000000100'),
    ('VENT III', '110001000011'),
    ('VENT OFF', '110000010000'),
]

REPEATS = 6  # frames de datos tras los dos preambulos


def frame_raw(bits12):
    vals = []
    for i, b in enumerate(bits12):
        if i < 11:
            if b == '1':
                vals += [MARK_LONG, SPACE_SHORT]
            else:
                vals += [MARK_SHORT, SPACE_LONG]
        else:
            if b == '1':
                vals += [MARK_LONG, BIT12_SPACE_1]
            else:
                vals += [MARK_SHORT, BIT12_SPACE_0]
    return vals


def button_raw(code):
    frames = [PREAMBLE1, PREAMBLE2] + [code] * REPEATS
    seq = []
    for idx, f in enumerate(frames):
        vals = frame_raw(f)
        if idx == len(frames) - 1:
            vals[-1] = TRAILING_GAP  # ultimo espacio = hueco final largo
        seq += vals
    return ' '.join(str(v) for v in seq)


def next_version(base):
    import glob, re
    nums = []
    for f in glob.glob(os.path.join(base, 'mando_v*.irplus')):
        m = re.search(r'mando_v(\d+)\.irplus', os.path.basename(f))
        if m:
            nums.append(int(m.group(1)))
    return (max(nums) + 1) if nums else 1


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(base, f'mando_v{next_version(base)}.irplus')
    lines = []
    lines.append('<irplus>')
    lines.append('<device manufacturer="Desconocido" model="Extractor con luz y 3 velocidades" columns="2" format="WINLIRC_RAW" frequency="38000">')
    lines.append('')
    for label, code in BUTTONS:
        raw = button_raw(code)
        lines.append(f'<button label="{label}" alt="{label}" labelSize="18.0" span="24">{raw}</button>')
    lines.append('')
    lines.append('</device>')
    lines.append('</irplus>')
    content = '\n'.join(lines) + '\n'
    with open(out, 'w') as f:
        f.write(content)
    print(content)


if __name__ == '__main__':
    main()
