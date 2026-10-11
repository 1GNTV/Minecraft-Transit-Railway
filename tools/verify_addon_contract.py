"""Check addon-facing private and public descriptors in the actual shipped classes.

This is a bytecode contract check, not a live Forge/Mixin integration test.
The baseline is the user-tested db74325 artifact; synthetic lambdas are excluded.
"""
import json
from pathlib import Path
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / 'tools/fixtures/train-addon-contract.json'


def members(data):
    offset = 0

    def read(size):
        nonlocal offset
        result = data[offset:offset + size]
        if len(result) != size:
            raise ValueError('Truncated class file')
        offset += size
        return result

    def u2():
        return struct.unpack('>H', read(2))[0]

    def attributes():
        for _ in range(u2()):
            u2()
            read(struct.unpack('>I', read(4))[0])

    if read(4) != b'\xca\xfe\xba\xbe':
        raise ValueError('Invalid class file')
    read(4)
    count = u2()
    pool = {}
    index = 1
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
             11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = read(1)[0]
        if tag == 1:
            pool[index] = read(u2()).decode('utf-8', errors='replace')
        else:
            read(sizes[tag])
        index += 2 if tag in (5, 6) else 1
    read(6)
    read(2 * u2())
    result = []
    for kind in ('field', 'method'):
        for _ in range(u2()):
            flags, name, descriptor = u2(), u2(), u2()
            attributes()
            if not flags & 0x1000 and pool[name] != '<clinit>':
                result.append({'kind': kind, 'name': pool[name],
                               'descriptor': pool[descriptor], 'flags': flags})
    return result


def verify(path):
    baseline = json.loads(BASELINE.read_text(encoding='utf-8'))
    with zipfile.ZipFile(path) as jar:
        total = 0
        for target, expected in baseline['classes'].items():
            current = members(jar.read(target.replace('.', '/') + '.class'))
            for item in expected:
                assert item in current, f'Addon contract changed: {target} {item}'
            # TJMetro selects by method name, not an explicit descriptor. An overload
            # could also be selected by Mixin, so retaining one bridge is insufficient.
            if target == 'org.mtr.mod.render.RenderVehicles':
                methods = [m for m in current if m['kind'] == 'method' and m['name'] == 'renderConnection']
                assert len(methods) == 1, 'Ambiguous TJMetro renderConnection injection target'
                assert methods[0]['descriptor'] == baseline['tjmetro_target_descriptor']
            total += len(expected)
    print(f'VERIFIED {total} train addon member contracts, including exact TJMetro callback target (bytecode only)')


if __name__ == '__main__':
    for path in sys.argv[1:]:
        verify(path)
