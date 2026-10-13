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
