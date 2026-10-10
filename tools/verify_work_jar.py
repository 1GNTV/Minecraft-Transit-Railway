"""Reject incomplete Forge artifacts and unregistered rendering mixins."""
import glob
import hashlib
import json
from pathlib import Path
import subprocess
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
    # Validate stable third-party targets against the dependency actually shipped.
    targets = {
        'org.mtr.mapping.mapper.OptimizedRenderer': ['render(boolean)', 'BatchManager.drawAll:'],
        'org.mtr.mapping.mapper.OptimizedModel': ['fromObjModels(', 'DEFAULT_MAPPING;'],
        'org.mtr.mapping.mapper.OptimizedModel$ObjModel': ['RawModel rawModel;'],
        'org.mtr.mapping.render.batch.BatchManager': ['translucentBatches;', 'drawAll('],
        'org.mtr.mapping.render.shader.ShaderManager': ['setupShaderBatchState(', 'cleanupShaderBatchState()'],
        'org.mtr.mapping.render.object.VertexArray': ['VertexAttributeMapping mapping;', 'draw()'],
        'org.mtr.mapping.render.vertex.VertexAttributeState': ['VertexAttributeType.values:', 'java/nio/ByteBuffer.allocate:', 'org/lwjgl/opengl/GL33.glVertexAttrib4f:'],
    }
    for target, expected in targets.items():
        result = subprocess.run(['javap', '-p', '-c', '-classpath', path, target], capture_output=True, text=True, check=True)
        for marker in expected:
            assert marker in result.stdout, f'Unsupported mixin target in {target}: {marker}'
    print('VERIFIED mapping method/field/invocation targets (static check, not live Mixin application)')
    print('JAR SHA256', hashlib.sha256(Path(path).read_bytes()).hexdigest())
