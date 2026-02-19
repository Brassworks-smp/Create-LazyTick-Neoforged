package net.pinkcats.createlazytick.helper.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.pinkcats.createlazytick.manager.LazyTickStatCache;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public enum LazyTickSortMode {

    DEFAULT("default", (e1, e2, source) -> {
        BlockPos p1 = e1.getKey();
        BlockPos p2 = e2.getKey();
        if (p1.getX() != p2.getX()) return Integer.compare(p1.getX(), p2.getX());
        if (p1.getY() != p2.getY()) return Integer.compare(p1.getY(), p2.getY());
        return Integer.compare(p1.getZ(), p2.getZ());
    }),

    NEAREST("nearest", (e1, e2, source) -> {

        Entity player = source.getEntity();
        if (player == null) {
            return 0; 
        }

        Vec3 playerVec = player.position();

        double dist1 = e1.getKey().distToCenterSqr(playerVec.x, playerVec.y, playerVec.z);
        double dist2 = e2.getKey().distToCenterSqr(playerVec.x, playerVec.y, playerVec.z);

        return Double.compare(dist1, dist2);
    }),

    TIME("time", (e1, e2, source) ->
            Long.compare(e2.getValue().getRegisteredTime(), e1.getValue().getRegisteredTime())),

    NAME("name", (e1, e2, source) ->
            e1.getValue().getBlockId().compareTo(e2.getValue().getBlockId())),

    PLAYER("player", (e1, e2, source) ->
            e1.getValue().getOwnerName().compareTo(e2.getValue().getOwnerName())),

    MODE("method", (e1, e2, source) -> {

        return Boolean.compare(e2.getValue().isForced(), e1.getValue().isForced());
    }),

    VALUE("value", (e1, e2, source) ->
            Integer.compare(e1.getValue().getScrollValue(), e2.getValue().getScrollValue())),

    LOADED("loaded", (e1, e2, source) -> 0);

    private final String id;
    private final SortLogic logic;

    LazyTickSortMode(String id, SortLogic logic) {
        this.id = id;
        this.logic = logic;
    }

    public String getId() {
        return id;
    }

    public Comparator<Map.Entry<BlockPos, LazyTickStatCache>> getThreadSafeComparator(
            Set<BlockPos> loadedPositions, Vec3 playerPos, boolean reverse) {
        return (e1, e2) -> {
            int result;

            if (this == LOADED) {

                boolean isLoaded1 = loadedPositions.contains(e1.getKey());
                boolean isLoaded2 = loadedPositions.contains(e2.getKey());
                result = Boolean.compare(isLoaded2, isLoaded1);
            } else if (this == NEAREST) {

                if (playerPos == null) {
                    result = 0; 
                } else {

                    double d1 = e1.getKey().distToCenterSqr(playerPos.x, playerPos.y, playerPos.z);
                    double d2 = e2.getKey().distToCenterSqr(playerPos.x, playerPos.y, playerPos.z);
                    result = Double.compare(d1, d2);
                }
            } else {

                result = this.logic.compare(e1, e2, null);
            }

            return reverse ? -result : result;
        };
    }

    @Nullable
    public static LazyTickSortMode byName(String name) {
        for (LazyTickSortMode mode : values()) {
            if (mode.id.equalsIgnoreCase(name)) return mode;
        }
        return null;
    }

    @FunctionalInterface
    interface SortLogic {
        int compare(Map.Entry<BlockPos, LazyTickStatCache> e1, Map.Entry<BlockPos, LazyTickStatCache> e2, CommandSourceStack source);
    }
}