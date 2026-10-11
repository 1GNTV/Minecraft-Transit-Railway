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
        assert 'mtr.text.performance.mixins.json' in manifest, 'Unregistered world text mixin'
        text_config=json.loads(jar.read('mtr.text.performance.mixins.json'))
        for name in text_config['client']:assert text_config['package'].replace('.','/')+'/'+name+'.class' in jar.namelist()
        assert 'org/mtr/init/ForgeWorldTextBatch.class' in jar.namelist()
        config = json.loads(jar.read('mtr.performance.mixins.json'))
        for name in config['client']:
            entry = config['package'].replace('.', '/') + '/' + name + '.class'
            assert entry in jar.namelist(), f'Missing {entry}'
        for name in ['OpaqueMeshCombiner', 'GlVertexAttributeCache', 'MatrixUploadBuffer', 'RenderPipelineMetrics', 'ModelMeshCompiler', 'GpuPassTimer', 'RailBatchGeometry', 'RailGeometrySnapshots', 'RailSectorCache', 'RailSectorBatcher', 'GpuMeshOwnership', 'GenerationUploadQueue', 'NativeImageCropper']:
            assert f'org/mtr/mod/render/{name}.class' in jar.namelist(), name
        for name in ['InstancedModelRenderer', 'ConsecutiveRuns', 'FrameMemoizer', 'TriangleOrder', 'OpaqueMeshOptimizer', 'RenderCallData', 'VertexArrayIdentity', 'ShaderReloadPolicy', 'OculusInstanceRenderer', 'InstanceShaderSource', 'TrainVisibility', 'PredictiveVehiclePreloader', 'StaticRegionRenderer', 'StaticDecorationRegions', 'PersistentRegionCache', 'RegionVertexRanges', 'ModelDetailGeometry', 'ModelDetailCache', 'ModelPartVisibility', 'ShadowPassState', 'WorldTextBatch', 'InstanceValues', 'RegionPageLayout', 'BoundedDrawGrouping', 'BoundedMeshSimplifier', 'ScreenErrorBudget']:
            assert f'org/mtr/mod/render/{name}.class' in jar.namelist(), name
        assert 'org/mtr/mod/resource/WeakExpiryQueue.class' in jar.namelist()
        assert 'org/mtr/init/PerformanceFrameHooks.class' in jar.namelist(), 'Missing Forge render frame hooks'
        for name in ['batch/BatchManager', 'vertex/VertexAttributeState', 'object/VertexArray']:
            assert f'org/mtr/mapping/render/{name}.class' in jar.namelist(), name
        forbidden = ['ShaderInstanceMixin', 'VertexArrayMixin', 'ShaderManagerMixin']
        assert not any(name in config['client'] for name in forbidden), 'V3 bypass included'
        assert jar.testzip() is None, 'Corrupt JAR'
    print('VERIFIED', Path(path).name, Path(path).stat().st_size)
    # Validate stable third-party targets against the dependency actually shipped.
    targets = {
        'org.mtr.mapping.mapper.GraphicsHolder':['matrixStack;', 'drawContext;', 'drawText(org.mtr.mapping.holder.MutableText', 'drawText(org.mtr.mapping.holder.OrderedText', 'drawText(java.lang.String', 'createVertexConsumer(', 'drawTextureInWorld(', 'drawLineInWorld(', 'renderEntity('],
        'org.mtr.mapping.render.shader.ModShaderHandler': ['renderingShadows()', 'java/lang/Thread.getStackTrace:'],
        'org.mtr.mapping.mapper.OptimizedRenderer': ['render(boolean)', 'BatchManager.drawAll:'],
        'org.mtr.mapping.mapper.OptimizedModel': ['fromObjModels(', 'DEFAULT_MAPPING;'],
        'org.mtr.mapping.mapper.OptimizedModel$ObjModel': ['RawModel rawModel;', 'ObjModel(java.util.List<org.mtr.mapping.render.model.RawMesh>, boolean, float, float, float, float, float, float)'],
        'org.mtr.mapping.mapper.OptimizedModel$MaterialGroup': ['MaterialProperties materialProperties;', 'modelPartConsumers;'],
        'org.mtr.mapping.render.vertex.CapturingVertexConsumer': ['RawModel rawModel;', 'beginStage('],
        'org.mtr.mapping.render.model.RawModel': ['triangulate()', 'distinct()', 'iterateRawMeshList(', 'lambda$upload$0(', 'RawMesh.upload:', 'VertexArray."<init>":'],
        'org.mtr.mapping.render.model.RawMesh': ['Vertex> vertices;', 'Face> faces;', 'applyMatrix(', 'Mesh upload('],
        'org.mtr.mapping.render.vertex.VertexAttributeMapping$Builder': ['set(', 'build()'],
        'org.mtr.mapping.render.tool.Utilities': ['exchangeLightmapUVBits(', 'canUseCustomShader()'],
        'org.mtr.mapping.render.batch.BatchManager$RenderCall': ['VertexArray vertexArray;', 'VertexAttributeState vertexAttributeState;', 'draw()'],
        'org.mtr.mapping.render.batch.BatchManager': ['translucentBatches;', 'drawAll(', 'lambda$drawBatch$2(', 'java/util/List.forEach:'],
        'org.mtr.mapping.render.shader.ShaderManager': ['setupShaderBatchState(', 'cleanupShaderBatchState()', 'reloadShaders()', 'shaders;', 'rendertype_entity_cutout', 'rendertype_entity_translucent_cull', 'rendertype_beacon_beam'],
        'org.mtr.mapping.render.object.VertexArray': ['private int id;', 'VertexAttributeMapping mapping;', 'draw()', 'close()', 'VertexArray(org.mtr.mapping.render.object.VertexArray, org.mtr.mapping.render.batch.MaterialProperties)'],
        'org.mtr.mapping.render.vertex.VertexAttributeState': ['VertexAttributeType.values:', 'java/nio/ByteBuffer.allocate:', 'org/lwjgl/opengl/GL33.glVertexAttrib4f:', 'Utilities.canUseCustomShader:'],
    }
    for target, expected in targets.items():
        result = subprocess.run(['javap', '-p', '-c', '-classpath', path, target], capture_output=True, text=True, check=True)
        for marker in expected:
            assert marker in result.stdout, f'Unsupported mixin target in {target}: {marker}'
    print('VERIFIED mapping method/field/invocation targets (static check, not live Mixin application)')
    print('JAR SHA256', hashlib.sha256(Path(path).read_bytes()).hexdigest())
