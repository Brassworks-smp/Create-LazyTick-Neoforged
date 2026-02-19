package net.pinkcats.createlazytick.bridge.Create;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.pinkcats.createlazytick.helper.tooltip.LazyTickTier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface ISmartBlockEntityControl {

    String createLazyTick$getOwnerName();
    void createLazyTick$setOwnerName(String value);

    UUID createLazyTick$getOwnerUUID();
    void createLazyTick$setOwnerUUID(UUID uuid);

    BlockPos CLT$getPos();
    ResourceKey<Level> CLT$getDimension();

    void lazytick$setSyncedTier(int currentTick, int maxTick);

    LazyTickTier lazytick$getSyncedTier();

    boolean lazytick$isDefaultState();

    default int createLazyTick$getDynamicValue() { return 100; }
    default void createLazyTick$setDynamicValue(int value) {}

    default int createLazyTick$getForcedValue() { return -1; }
    default void createLazyTick$setForcedValue(int value) {}

    void createLazyTick$setDelayForced(boolean isForced);
    boolean createLazyTick$isDelayForced();

    void createLazyTick$setCurrentSuperTick(int tick);
    int createLazyTick$getCurrentSuperTick();

    default List<Component> createLazyTick$getCustomTooltipInfo() {
        return new ArrayList<>();
    }

    default boolean createLazyTick$shouldRenderTier() {
        return true;
    }

    default boolean createLazyTick$shouldRenderMode() {
        return true;
    }

    default void CLT$onClientRequest(int extraData) {}

    void lazytick$setExtraData(int data);

    int lazytick$getExtraData();

    void createLazyTick$sendBlockUpdated();

    boolean CLT$IsController();
}