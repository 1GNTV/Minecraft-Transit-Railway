"""Reject incomplete Forge artifacts and unregistered rendering mixins."""
import glob
import json
from pathlib import Path
import zipfile

jars = glob.glob('forge/build/libs/*-all.jar')
assert jars, 'Missing shaded Forge JAR'
for path in jars:
    with zipfile.ZipFile(path) as jar:
        manifest = jar.read('META-INF/MANIFEST.MF').decode()
        assert 'mtr.performance.mixins.json' in manifest, 'Unregistered Forge performance mixins'
        config = json.loads(jar.read('mtr.performance.mixins.json'))
        for name in config['client']:
            entry = config['package'].replace('.', '/') + '/' + name + '.class'
            assert entry in jar.namelist(), f'Missing {entry}'
        for name in ['OpaqueMeshCombiner', 'GlVertexAttributeCache', 'MatrixUploadBuffer', 'RenderPipelineMetrics']:
            assert f'org/mtr/mod/render/{name}.class' in jar.namelist(), name
        for name in ['batch/BatchManager', 'vertex/VertexAttributeState', 'object/VertexArray']:
            assert f'org/mtr/mapping/render/{name}.class' in jar.namelist(), name
        forbidden = ['ShaderInstanceMixin', 'VertexArrayMixin', 'ShaderManagerMixin']
        assert not any(name in config['client'] for name in forbidden), 'V3 bypass included'
        assert jar.testzip() is None, 'Corrupt JAR'
    print('VERIFIED', Path(path).name, Path(path).stat().st_size)
