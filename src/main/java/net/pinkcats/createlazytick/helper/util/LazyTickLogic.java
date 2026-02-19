package net.pinkcats.createlazytick.helper.util;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;
import net.pinkcats.createlazytick.helper.tooltip.LazyTickTooltipWhiteList;
import net.pinkcats.createlazytick.manager.ForcedActiveManager;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public class LazyTickLogic {
    @Nullable
    public static <T extends BlockEntityBehaviour> T getBehaviour(SmartBlockEntity be, Class<T> type) {
        if (be == null) return null;

        for (BlockEntityBehaviour behaviour : be.getAllBehaviours()) {
            if (type.isInstance(behaviour)) {
                return type.cast(behaviour);
            }
        }
        return null;
    }

    public static void switchMode(ISmartBlockEntityControl control, boolean isForcedMode, int value) {
        if (isForcedMode) {

            control.createLazyTick$setForcedValue(value);

            control.createLazyTick$setDynamicValue(100);
        } else {

            control.createLazyTick$setDynamicValue(value);

            control.createLazyTick$setForcedValue(-1);
        }
    }

    public static void updateState(ISmartBlockEntityControl control) {
        if (!(control instanceof BlockEntity be) || be.getLevel() == null || be.getLevel().isClientSide) {
            return;
        }

        LazyTickTooltipWhiteList whiteItem = LazyTickTooltipWhiteList.getByEntity(be);
        if (whiteItem == null) {
            return;
        }
        int maxConfigDelay = whiteItem.getMaxTick(); 

        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        String blockName = Objects.requireNonNull(RegistriesWrapper.BLOCKS.getKey(be.getBlockState().getBlock())).toString();
        String playerName = control.createLazyTick$getOwnerName();
        UUID playerUUID = control.createLazyTick$getOwnerUUID();

        int dyn = control.createLazyTick$getDynamicValue();
        int frc = control.createLazyTick$getForcedValue();

        if (frc != -1) {
            ForcedActiveManager.register(level, pos, blockName, playerUUID, playerName, frc, true);
            control.createLazyTick$setDelayForced(true);
            if (frc == 0) {

                control.createLazyTick$setCurrentSuperTick(1);
            } else {

                int targetTick = Math.max(1, (int) ((frc / 100.0f) * maxConfigDelay));
                control.createLazyTick$setCurrentSuperTick(targetTick);
            }
        } else { 
            control.createLazyTick$setDelayForced(false); 

            if (dyn == 100) {

                ForcedActiveManager.unregister(level, pos);
                control.createLazyTick$setCurrentSuperTick(1);
            } else {

                ForcedActiveManager.register(level, pos, blockName, playerUUID, playerName, dyn, false);

                if (dyn <= 0) {
                    control.createLazyTick$setCurrentSuperTick(1);
                }

                int effectiveMaxLimit = Math.max(1, (int) ((dyn / 100.0f) * maxConfigDelay));

                int currentInterval = control.createLazyTick$getCurrentSuperTick();

                if (currentInterval > effectiveMaxLimit) {
                    control.createLazyTick$setCurrentSuperTick(effectiveMaxLimit);
                }

            }
        }
    }

    public static int computeNextInterval(ISmartBlockEntityControl control, int currentInterval, int maxConfigInterval) {

        if (control.createLazyTick$isDelayForced()) {

            int frc = control.createLazyTick$getForcedValue();
            if (frc == 0) return 1; 
            return Math.max(1, (int) (maxConfigInterval * (frc / 100.0f)));
        }

        int nextInterval = currentInterval + Math.max(1, currentInterval / 10);

        int dynamicPercent = control.createLazyTick$getDynamicValue();

        if (dynamicPercent <= 0) dynamicPercent = 100;

        int effectiveMax = (int) (maxConfigInterval * (dynamicPercent / 100.0f));
        effectiveMax = Math.max(1, effectiveMax);

        return Math.min(nextInterval, effectiveMax);
    }

    public static void setIntervalSafe(ISmartBlockEntityControl control, int interval) {
        if (!control.createLazyTick$isDelayForced()) {
            control.createLazyTick$setCurrentSuperTick(interval);
        }
    }
}