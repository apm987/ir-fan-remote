#!/usr/bin/env python3
import sys, os, glob

def classify(v):
    if v > 5000:
        return 'GAP'
    if v > 700:
        return 'L'  # long
    return 'S'  # short

def decode_subframe(elems):
    # elems: list of ints, 23 elements
    bits = []
    for i in range(0, len(elems) - 1, 2):
        mark = elems[i]
        space = elems[i + 1]
        if mark > 700 and space < 700:
            bits.append('1')
        elif mark < 700 and space > 700:
            bits.append('0')
        else:
            bits.append('?')
    return bits, elems[-1]

def parse(raw):
    nums = [int(x) for x in raw.strip().split(',') if x.strip() != '']
    # split into subframes on gaps
    subframes = []
    gaps = []
    cur = []
    for v in nums:
        if v > 5000:
            if cur:
                subframes.append(cur)
            gaps.append(v)
            cur = []
        else:
            cur.append(v)
    if cur:
        subframes.append(cur)
    return subframes, gaps

def main():
    base = os.path.dirname(os.path.abspath(__file__))
    files = sorted(glob.glob(os.path.join(base, 'capturas', '*.raw')))
    for f in files:
        name = os.path.basename(f)
        raw = open(f).read()
        subframes, gaps = parse(raw)
        print('=' * 70)
        print(name, 'subframes=', len(subframes))
        for idx, sf in enumerate(subframes):
            bits, trailing = decode_subframe(sf)
            g = gaps[idx] if idx < len(gaps) else 0
            trail = 'L' if trailing > 700 else 'S'
            print(f'  sf{idx+1:2d} len={len(sf):2d} bits={"".join(bits)} trail={trail} gap_prev={g}')

if __name__ == '__main__':
    main()
