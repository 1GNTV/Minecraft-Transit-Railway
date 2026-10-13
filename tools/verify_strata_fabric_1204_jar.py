#!/usr/bin/env python3
"""Validate exactly the JAR that is to be released, not the unremapped dev JAR."""
from pathlib import Path
import json,sys,zipfile
jar=Path(sys.argv[1]);assert jar.is_file(),jar
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    names=set(z.namelist())
    for name in ("fabric.mod.json", "mtr.strata.performance.mixins.json",
                 "org/mtr/mod/render/strata/StrataRailEngine.class",
                 "org/mtr/mod/render/strata/StrataVehiclePositionCache.class",
                 "org/mtr/mod/render/RenderVehicles.class",
                 "org/mtr/mod/render/RenderRails.class"):
        assert name in names,f"Missing {name}"
    metadata=json.loads(z.read("fabric.mod.json"))
    assert metadata["id"]=="mtr" and metadata["depends"]["minecraft"]=="1.20.4"
    assert "mtr.strata.performance.mixins.json" in metadata["mixins"]
    assert len(z.read("org/mtr/mod/render/RenderVehicles.class"))>0
    print("PASS STRATA Fabric 1.20.4 JAR: one mtr mod, valid metadata, STRATA core and perf mixins")
