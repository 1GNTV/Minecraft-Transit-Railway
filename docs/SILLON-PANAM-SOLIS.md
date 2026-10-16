# PANAM 1:1 — Sillon SOLIS (Forge 1.20.1 + Fabric 1.20.4)

Status: source integrated on **both branches**; references are the user's real photos of RATP line 10 and line 12 double-sided suspended platform displays, from front, underneath and side. These source changes do not assert pixel-perfect physical photogrammetry. Runtime visual inspection is required.

## 3D geometry
- Exactly **two blocks wide**, preserving MTR's existing horizontal twin-block state and platform/arrival configuration.
- The screen is **13.15 model pixels high** (was 11.75), with its upper edge at 12.75 model pixels. Its lower edge extends 0.40 px below block baseline. Pixel layout stays 1024 x 466, preserving the 2 ETA slots, central separator, upper white destination strip and information panel.
- The render transform applies a **2.6° outward-facing lean around the screen's upper edge**. Both player-facing display surfaces lean toward their respective platform.
- The block-model silhouette now contains an independent central black housing/spine, paired stepped-profile thin display cartridges, metal separation strips, a projecting lower lip with underside and **one narrow ceiling suspension rod per block**. The repeated two-block placement creates two rods spread over the full width, not one fat centre pole. Three subtle casing tiers approximate the screen tilt without introducing invalid non-22.5° Minecraft JSON model element rotations.
- The line-coloured horizontal strip is still drawn from the actual MTR route colour by the existing PanamSurface GPU texture. The static mechanical underside uses the metal texture, because line tint is dynamic and must not be hardcoded to the line 12 green.
- Existing line/mixed-departure/clock/pictogram visuals remain; the panel is not globally scaled arbitrarily and the old MTR block ID remains unchanged.

## Traffic bulletin timeline
- **Important DANGER or STOP bulletin for the currently displayed route**: 12 seconds per display, interleaved with noncritical network bulletins rather than monopolising the display.
- **Ordinary line notice and planned works**: 6.5 seconds, no critical priority.
- **General network/other-line bulletin**: 4.5 seconds.
- If a critical event exists alongside ordinary bulletins, the order is **critical / next network bulletin / critical / next network bulletin**, advancing the ordinary-bulletin cursor fairly.
- With no critical event, rotate all messages in order. Display clocks advance independently. Changing arrivals alone does not reset the bulletin cycle.
- Every event is still drawn by the existing independent right-hand column while the left-side train ETAs continue to update.
- Current-route critical alerts can displace one lower-priority bulletin if the list is full, avoiding lost hazard reports. Global network notices are still included.
- Only actual MTR/PN2 operator data is shown; **do not fabricate real-world alerts**.

## Validation and rollback
- Test `python3 tools/test_panam_pixel.py` (real compositing oracle + timeline + 3D silhouette invariants), `test_panam_camera_order.py`, `test_panam_eta_contrast.py`, `test_panam_arrival_parity.py` and `test_panam_lifecycle.py`.
- Compile and validate a fresh **Fabric 1.20.4** jar. Forge 1.20.1 receives exactly the same changes in its shared `fabric/src/main/java` and assets tree, but is not compiled unless requested.
- Check in-game front and underside screenshots, line 10/12 colour strip, Oculus, JCM/MTR France, shader placement, billboard overlay depth, and actual rendering at all four facing directions.
- Preserve prior SOLIS commits as rollback points. This pass changes no core MTR/JCM ABI.
