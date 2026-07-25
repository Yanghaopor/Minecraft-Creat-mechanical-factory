package com.example.examplemod.compatibility;

import com.example.examplemod.ExampleMod;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Soft integration with Sable, the physics backend Create: Aeronautics flies its
 * airships on. A factory host block deployed onto a moving platform keeps a stable
 * {@code BlockPos} inside the platform's moving sub-level, while its real
 * parent-world position changes every tick. Projecting that stored position through
 * the sub-level's <em>live</em> transform yields where the host block actually is
 * right now, so a player leaving the factory steps out onto the ship wherever it
 * has flown rather than where it was when they entered.
 *
 * <p>Uses reflection to call Sable's companion API, so Sable stays a pure soft
 * dependency: the resolver class is never loaded when Sable is absent and exit
 * falls back to the stored host/entry position.
 */
public final class SableHostPosition {
    private static final String COMPANION_CLASS = "dev.ryanhcode.sable.companion.SableCompanion";
    @Nullable
    private static volatile CompanionApi companionApi;
    private static volatile boolean companionLookupAttempted;

    private SableHostPosition() {}

    /**
     * Projects a host block's sub-level-local position to the parent world.
     * The hostPos stored in the room record is in the sub-level's local coordinate
     * system (if the host is on a moving platform). Sable's public Companion API owns
     * the lookup and transformation; reaching into Sable's internal {@code SubLevel}
     * implementation would break whenever that implementation changes.
     *
     * @param hostLevel the level containing the host block
     * @param subLevelLocalPos a position in the sub-level's local coordinate system
     *                         (typically hostPos + some offset)
     * @return the position in the parent world, including the unchanged input when it
     *         is not in a sub-level; {@code null} when no safe projection is available
     */
    public static Vec3 projectOutOfSubLevel(ServerLevel hostLevel, Vec3 subLevelLocalPos) {
        CompanionApi api = getCompanionApi();
        if (api == null) {
            return null;
        }
        try {
            Object projected = api.projectOutOfSubLevel().invoke(api.instance(), hostLevel, subLevelLocalPos);
            return projected instanceof Vec3 result ? result : null;
        } catch (Throwable t) {
            // Never hand an unprojected sub-level coordinate back to the caller:
            // teleporting to it can make Sable construct an enormous collision AABB.
            ExampleMod.LOGGER.warn("Sable exit projection failed; using the safe return point", t);
            return null;
        }
    }

    /** True when a local position belongs to a live Sable sub-level plot. */
    public static boolean isInSubLevel(ServerLevel level, BlockPos pos) {
        CompanionApi api = getCompanionApi();
        if (api == null) {
            return false;
        }
        try {
            return api.getContaining().invoke(api.instance(), level, pos.getX() >> 4, pos.getZ() >> 4) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * True for coordinates reserved by Sable's private plot grid, including a
     * plot whose physical structure has already been removed. Such coordinates
     * must never be used as ordinary parent-world teleport destinations.
     */
    public static boolean isInPlotGrid(ServerLevel level, BlockPos pos) {
        CompanionApi api = getCompanionApi();
        if (api == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(api.isInPlotGrid().invoke(api.instance(), level,
                    pos.getX() >> 4, pos.getZ() >> 4));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Detects both ways Sable represents a player on an airship: physically in
     * its plot, or following its live transform from the parent world.
     */
    public static boolean isPlayerOnSubLevel(ServerPlayer player) {
        CompanionApi api = getCompanionApi();
        if (api == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        try {
            return api.getTrackingSubLevel().invoke(api.instance(), player) != null
                    || api.getContaining().invoke(api.instance(), level,
                    player.chunkPosition().x, player.chunkPosition().z) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Clears Sable's current and previous sub-level tracking after a player has
     * been placed at the real parent-world position. Both setters belong to
     * Sable's public mix-in interface and are accessed reflectively to preserve
     * this mod's optional dependency.
     */
    public static void clearPlayerSubLevelTracking(ServerPlayer player) {
        for (Method method : player.getClass().getMethods()) {
            String name = method.getName();
            if (method.getParameterCount() != 1
                    || (!name.equals("sable$setTrackingSubLevel")
                        && !name.equals("sable$setLastTrackingSubLevelID"))) {
                continue;
            }
            try {
                method.invoke(player, (Object) null);
            } catch (Throwable ignored) {
                // Sable's API is optional and must never make normal factory
                // entry fail when a compatible method is unavailable.
            }
        }
    }

    @Nullable
    private static CompanionApi getCompanionApi() {
        CompanionApi cached = companionApi;
        if (cached != null || companionLookupAttempted) {
            return cached;
        }
        synchronized (SableHostPosition.class) {
            if (companionApi != null || companionLookupAttempted) {
                return companionApi;
            }
            companionLookupAttempted = true;
            try {
                Class<?> companionClass = Class.forName(COMPANION_CLASS);
                Object instance = companionClass.getField("INSTANCE").get(null);
                companionApi = new CompanionApi(instance,
                        companionClass.getMethod("projectOutOfSubLevel", Level.class, Vec3.class),
                        companionClass.getMethod("getContaining", Level.class, int.class, int.class),
                        companionClass.getMethod("isInPlotGrid", Level.class, int.class, int.class),
                        companionClass.getMethod("getTrackingSubLevel", Entity.class));
            } catch (Throwable ignored) {
                // Sable (or its bundled Companion API) is absent. All callers
                // have a regular-Minecraft fallback.
            }
            return companionApi;
        }
    }

    private record CompanionApi(Object instance, Method projectOutOfSubLevel,
                                Method getContaining, Method isInPlotGrid,
                                Method getTrackingSubLevel) {}
}
