"""Derive an offline test jar from an SRG mod; never modify or redistribute the input.

Only exact SRG member identifiers in class constant pools are renamed. The mapping
must give every SRG name a unique MCP name. Bytecode instructions remain unchanged.
"""
import hashlib
import json
import pathlib
import struct
import sys
import zipfile

source, mapping, output = map(pathlib.Path, sys.argv[1:])
names = {}
for line in mapping.read_text().splitlines():
    parts = line.split()
    if parts[0] not in ('MD:', 'FD:'):
        continue
    old = parts[1].rsplit('/', 1)[-1].encode()
    new = parts[3 if parts[0] == 'MD:' else 2].rsplit('/', 1)[-1].encode()
    if old in names and names[old] != new:
        raise ValueError('Ambiguous member mapping: ' + old.decode())
    names[old] = new

def remap(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('Not a class file')
    count = struct.unpack_from('>H', data, 8)[0]
    result, at, index = bytearray(data[:10]), 10, 1
    widths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
              11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        kind = data[at]
        at += 1
        result.append(kind)
        if kind == 1:
            size = struct.unpack_from('>H', data, at)[0]
            value = data[at + 2:at + 2 + size]
            at += size + 2
            value = names.get(value, value)
            result.extend(struct.pack('>H', len(value)) + value)
        else:
            size = widths[kind]
            result.extend(data[at:at + size])
            at += size
            if kind in (5, 6):
                index += 1
        index += 1
    result.extend(data[at:])
    return result

output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(source) as original, zipfile.ZipFile(output, 'x', zipfile.ZIP_DEFLATED) as derived:
    for entry in original.infolist():
        if entry.filename.upper().startswith('META-INF/'):
            continue
        data = original.read(entry)
        derived.writestr(entry.filename, remap(data) if entry.filename.endswith('.class') else data)
receipt = {key: {'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
           for key, path in [('original', source), ('mapping', mapping), ('derived', output)]}
output.with_suffix('.receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt))
