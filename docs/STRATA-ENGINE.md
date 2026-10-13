# SILLON STRATA — engine architecture

STRATA is a code name for the SILLON renderer generation, not a new mod ID.
The distributable remains a **single Forge 1.20.1 mod JAR** with the original
`mtr` ID and Java packages for MTR addons. GLACIER Final b147 is the parent;
none of the AURORA experimental lighting changes have been merged.

## Contract

`RenderRails.render()` and `RenderVehicles.render()` stay at their original
addresses and preserve real JCM/MTR Scripting injection callbacks.
`StrataRailEngine` owns the indexed rail candidate selection, live occlusion/
shadow visibility policy, and 10s metrics. This first transition deliberately
does not rewrite every MTR method: future hot paths will be migrated behind
separately verified boundaries, one at a time.

Third-party addons can target synthetic javac lambdas and local captures.
Those are NOT stable Java APIs: for a particular JCM version, the release CI
must verify the actual compiled JAR descriptors and Mixin anchor sites.
We must never replace invoked callbacks with dead redirect stubs.

## Limits and test plan

* The source layout guard asserts original legacy local ordering and the 4
  call sites (`ordinal 0..2` plus second rail-builder call).
* The engine must preserve main-pass culling and shader shadow-pass semantics.
* CI must build the real shaded Forge JAR, not just compile helper classes.
* Full compatibility is not proven until a Forge client with JCM 2.3.0-beta.1,
  MTR Scripting and Oculus reaches a loaded world, opens the map, and renders
  a train and editor rails without Mixins failing.
* FPS must be compared scene-for-scene with GLACIER b147. Target ~170FPS is
  aspirational, not a measured STRATA gain.
* AURORA's overbright-rail regression is NOT claimed fixed in this baseline.
  Rail lightmap fixes require a separate controlled shader comparison.

Important: don't use `mtr` and `strata` as two distinct mod IDs.


## b162 JCM descriptor failure: nested train visibility

The strict b162 build gate caught the real failure: `RenderVehicles.lambda$render$12`
had the expected JCM model-render parameters prefixed by an extra captured
`boolean visualCar`. Renaming a method cannot fix an incompatible descriptor,
and weakening the verifier would just ship another crashing client.

STRATA now scopes the previously computed visual-car flag in a reentrant,
thread-owned `StrataVehicleVisibility` context around the synchronous
`VehicleResource.iterateModels` callback. The lambda reads the exact same
visibility decision without capturing an extra parameter. Exceptions and
nested renderer callbacks restore the previous state. The compiled JCM ABI
gate remains strict; no callbacks are faked. This adds one thread-local lookup
per model, not per vertex, and must still be benchmarked and runtime-tested.


## STRATA b164 — measured hotpath experiment

Spark flagged repeated `RailSectorBatcher.submitVisible` and
`StaticRegionRenderer.flush` during both Oculus shadow and main passes.
STRATA now caches **positive identity checks** on immutable visible-selection
lists returned by `SectorRenderPlanCache`. A stable page avoids recalculating
the entire `compiled.containsAll(visible)` membership every shader pass.
An immutable plan with a changed light/model/membership resolves to a new list,
and the fast path must therefore validate again. On GPU rebuild or eviction
the identities are cleared; mutable scratch lists never use this optimization.
The stored identity snapshots are capped at eight per page.

The renderer additionally reuses one `ArrayList<ScenePlan>` across sectors
rather than constructing a new list for every sector on every shadow cascade.
This list is **not** retained by the cache, which copies on cache misses.

Rollback this particular new experiment with
`-Dmtr.strata.immutableMasks=false`; the GLACIER caches and JCM callback
signatures otherwise remain unchanged. These are inferred hotpath
improvements, not a claimed FPS gain without comparable game benchmarks.


## STRATA b165 — single-JAR JCM vehicle-position cache

JCM 2.3.0-beta.1's `ModelPropertiesPartMixin.jsblock$hideDisplayParts`
calls `Vehicle.getVehicleCarsAndPositions()` even for non-DISPLAY
model parts. The scripting `VehicleResourceMixin` also calls it for
sound positioning. Repeated route and bogie reconstruction accounts for
the dominant extra CPU samples in the GLACIER-without-JCM vs
STRATA-with-JCM comparison.

SILLON now overrides this upstream non-final Java method in
`VehicleExtension`, preserving its Java binary descriptor and all
original JCM and MTR Scripting APIs. Original, unchanged JCM calls
automatically dispatch to the SILLON implementation: no patch mod,
extra dependencies, bundled JCM, or second SILLON JAR.

The per-vehicle `StrataVehiclePositionCache` retains a maximum of two
separate snapshots (smoothed and unsmoothed progress). Its keys include
render revision, exact progress bits, direction, path and vehicle-car
list identity, and rail-network revision; update, simulation, and disposal
clear it explicitly. Critically, each cache hit **returns a fresh deep
copy** of the mutable outer/inner arrays and position vectors, and
a cache miss stores a private deep-copy snapshot, keeping addons from
mutating the cache through their returned data.

Rollback with `-Dmtr.strata.vehiclePositionCache=false`.
`tools/test_strata_vehicle_positions.py` tests the real Java cache,
key isolation, revision handling and rollback before the Forge build.
The post-build JCM mixin-descriptor checks still run. Actual FPS gains
and client-side compatibility need a live world test with JCM,
MTR Scripting, Oculus, and the user's selected train resources.
