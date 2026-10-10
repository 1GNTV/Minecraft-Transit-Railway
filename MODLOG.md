# MTR Forge 1.20.1 optimization work

- 2026-10-11: Created `work/mtr-v4-optimization` remotely and locally from `perf/forge-1201-v4` / `2b048396`.
- Profiles reopened from prior conversation attachments, decoded locally as Spark protobuf. Both are 60-second V4 render-thread captures, not V2 baselines.
- Found V4 based on older `perf/forge-1201` / `0242e7b`, missing V2 render-pipeline improvements at `dadf9c9`.
- Exact Forge mapping dependency inspected with javap; current upstream source differs, so mixin targets were checked against shipped bytecode.
- Route: Forge 1.20.1 client-only mixins on MTR mapping classes, allocation restoration, safer shaderless attribute caching and equal-solid-material OBJ upload consolidation. Keep complete shader apply, VAO binds, queues, train/door transforms.
- No game profile/save/JAR changes performed. Original V2/V4 branches remain unchanged.
- Detailed source/profile findings, switches and verification limits: `docs/mtr-v4-work-optimization.md`.
- CI build pending; next: inspect compiler/test logs, repair issues, retrieve and verify shaded artifact, record evidence. In-game shader/addon A/B acceptance remains pending.
