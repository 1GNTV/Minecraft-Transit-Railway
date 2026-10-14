package org.mtr.mod.data;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.mapper.PersistenceStateExtension;
import org.mtr.mod.Init;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class is for storing extra world data that is not stored in Transport Simulation Core.
 * For example, "Disable Next Station Announcements" is a Minecraft-only setting which isn't tracked by Transport Simulation Core.
 */
public final class PersistentStateData extends PersistenceStateExtension {

	private final LongAVLTreeSet routeIdsWithDisabledAnnouncements = new LongAVLTreeSet();

	private static final String KEY_DATA = "route_ids_with_disabled_announcements";
	private static final String PANAM_GLOBAL = "sillon_panam_global";
	private static final String PANAM_ROUTES = "sillon_panam_route_ids";
	private final TreeMap<Long, String> panamRoutes = new TreeMap<>();
	private String panamGlobal = "";

	public PersistentStateData() {
		super(Init.MOD_ID);
	}

	@Override
	public void readNbt(CompoundTag compoundTag) {
		routeIdsWithDisabledAnnouncements.clear();
		for (final long routeId : compoundTag.getLongArray(KEY_DATA)) {
			routeIdsWithDisabledAnnouncements.add(routeId);
		}
		panamGlobal = compoundTag.getString(PANAM_GLOBAL);
		panamRoutes.clear();
		for (final long routeId : compoundTag.getLongArray(PANAM_ROUTES)) {
			if (panamRoutes.size() >= 128) break;
			String notice = compoundTag.getString("sillon_panam_route_" + routeId);
			if (!notice.isEmpty()) panamRoutes.put(routeId, notice);
		}
	}

	@Nonnull
	@Override
	public CompoundTag writeNbt2(CompoundTag compoundTag) {
		compoundTag.putLongArray(KEY_DATA, routeIdsWithDisabledAnnouncements.toLongArray());
		compoundTag.putString(PANAM_GLOBAL, panamGlobal);
		long[] ids = getPanamRouteIds();
		compoundTag.putLongArray(PANAM_ROUTES, ids);
		for (long id : ids) compoundTag.putString("sillon_panam_route_" + id, panamRoutes.get(id));
		return compoundTag;
	}


    public String getPanamGlobal() { return panamGlobal; }
    public String getPanamRoute(long id) { return panamRoutes.getOrDefault(id, ""); }
    public long[] getPanamRouteIds() { return panamRoutes.keySet().stream().mapToLong(Long::longValue).toArray(); }

    public void setPanamGlobal(String notice) {
        String next = sanitize(notice);
        if (!next.equals(panamGlobal)) { panamGlobal = next; markDirty2(); }
    }
    public void setPanamRoute(long id, String notice) {
        String next = sanitize(notice);
        if (!next.isEmpty() && !panamRoutes.containsKey(id) && panamRoutes.size() >= 128) return;
        if (next.isEmpty()) panamRoutes.remove(id); else panamRoutes.put(id, next);
        markDirty2();
    }
    private static String sanitize(String notice) {
        if (notice == null) return "";
        notice = notice.trim();
        return notice.length() > 16384 ? notice.substring(0, 16384) : notice;
    }

	public boolean getRouteIdHasDisabledAnnouncements(long routeId) {
		return routeIdsWithDisabledAnnouncements.contains(routeId);
	}

	public void setRouteIdHasDisabledAnnouncements(long routeId, boolean isDisabled) {
		if (isDisabled) {
			routeIdsWithDisabledAnnouncements.add(routeId);
		} else {
			routeIdsWithDisabledAnnouncements.remove(routeId);
		}
		markDirty2();
	}
}
