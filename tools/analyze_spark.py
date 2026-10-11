"""Read Spark sampler protobufs without dependencies; report sampled render costs.

Schema: lucko/spark spark-common/src/main/proto/spark/spark_sampler.proto.
No profile upload. CPU/wall samples are not GPU timers or FPS measurements.
"""
import argparse
import collections
import datetime
import hashlib
import json
import struct
from pathlib import Path


def varint(data, pos):
    value = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, pos
        shift += 7
        if shift > 63:
            raise ValueError('Invalid protobuf varint')


def fields(data):
    result = collections.defaultdict(list)
    pos = 0
    while pos < len(data):
        tag, pos = varint(data, pos)
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            value, pos = varint(data, pos)
        elif wire == 1:
            value = struct.unpack_from('<d', data, pos)[0]
            pos += 8
        elif wire == 2:
            length, pos = varint(data, pos)
            value = data[pos:pos + length]
            pos += length
        elif wire == 5:
            value = data[pos:pos + 4]
            pos += 4
        else:
            raise ValueError(f'Unsupported wire {wire}')
        result[number].append(value)
    return result


def ints(values):
    result = []
    for value in values:
        if isinstance(value, int):
            result.append(value)
        else:
            pos = 0
            while pos < len(value):
                n, pos = varint(value, pos)
                result.append(n)
    return result


def times(values):
    return sum(sum(struct.unpack('<' + 'd' * (len(v) // 8), v)) if isinstance(v, bytes) else v for v in values)


def string(message, number):
    return message.get(number, [b''])[0].decode('utf-8', errors='replace')


def analyze(path):
    raw = path.read_bytes()
    data = fields(raw)
    meta = fields(data[1][0])
    report = {'file': path.name, 'sha256': hashlib.sha256(raw).hexdigest(),
              'interval_us': meta.get(3), 'sampler_mode': meta.get(15, [0])[0],
              'sampler_engine': meta.get(16, [0])[0], 'threads': []}
    for number, name in [(2, 'start'), (11, 'end')]:
        value = meta.get(number, [0])[0]
        report[name] = datetime.datetime.fromtimestamp(value / 1000, datetime.timezone.utc).isoformat()
    report['mods'] = {}
    for entry in meta.get(13, []):
        pair = fields(entry)
        report['mods'][string(pair, 1)] = {str(k): [v.decode(errors='replace') if isinstance(v, bytes) else v for v in values] for k, values in fields(pair[2][0]).items()}
    for encoded_thread in data[2]:
        thread = fields(encoded_thread)
        name = string(thread, 1)
        total = times(thread[4])
        nodes = [fields(x) for x in thread.get(3, [])]
        inclusive = collections.defaultdict(float)
        exclusive = collections.defaultdict(float)
        paths = []
        def walk(index, ancestors):
            node = nodes[index]
            label = string(node, 3) + '.' + string(node, 4)
            cost = times(node[8])
            children = ints(node.get(9, []))
            if label not in ancestors:
                inclusive[label] += cost
            exclusive[label] += max(0, cost - sum(times(nodes[c][8]) for c in children))
            if any(term in label for term in ('BatchManager.drawAll', 'VertexAttributeState.apply', 'glDrawElements', 'ShaderInstance', 'Uniform.')):
                paths.append({'stack': ancestors + [label], 'ms': round(cost, 3), 'percent': round(cost / total * 100, 3)})
            for child in children:
                walk(child, ancestors + [label])
        for root in ints(thread.get(5, [])):
            walk(root, [])
        def ranking(values, limit):
            return [{'method': label, 'ms': round(cost, 3), 'percent': round(cost / total * 100, 3)} for label, cost in sorted(values.items(), key=lambda x: -x[1])[:limit]]
        item = {'name': name, 'sampled_ms': round(total, 3), 'nodes': len(nodes)}
        if 'Render' in name:
            item.update(inclusive=ranking(inclusive, 100), exclusive=ranking(exclusive, 60), hot_paths=sorted(paths, key=lambda x: -x['ms'])[:80])
        report['threads'].append(item)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('profiles', nargs='+', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps([analyze(p) for p in args.profiles], indent=2), encoding='utf-8')
