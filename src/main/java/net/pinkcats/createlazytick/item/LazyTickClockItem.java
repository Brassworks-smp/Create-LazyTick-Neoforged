package net.pinkcats.createlazytick.item;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pinkcats.createlazytick.config.ServerConfig;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;
import net.pinkcats.createlazytick.helper.util.LazyTickLogic;
import net.pinkcats.createlazytick.helper.LazyTickScrollBehaviour;
import net.pinkcats.createlazytick.helper.tooltip.LazyTickMode;
import net.pinkcats.createlazytick.helper.tooltip.LazyTickTooltipWhiteList;
import net.pinkcats.createlazytick.manager.ForcedActiveManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LazyTickClockItem extends Item {

    public LazyTickClockItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.createlazytick.clock.tooltip.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.createlazytick.clock.tooltip.line2")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {

        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (level.isClientSide || player == null)
            return InteractionResult.SUCCESS;

        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof ISmartBlockEntityControl control) {

            LazyTickTooltipWhiteList whiteItem = LazyTickTooltipWhiteList.getByEntity(be);

            if (whiteItem == null) {
                return InteractionResult.PASS;
            }

            if (whiteItem == LazyTickTooltipWhiteList.PUMP ||
                    whiteItem == LazyTickTooltipWhiteList.PIPE ||
                    whiteItem == LazyTickTooltipWhiteList.BELT) {
                player.displayClientMessage(Component.translatable("createlazytick.clock.global_config_locked")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }

            if (!ForcedActiveManager.canPlayerActivate(be, player)) {
                return InteractionResult.FAIL;
            }

            List<Integer> sequence = getSafeSequence();
            boolean targetIsDynamic = ServerConfig.getClockModeDefaultDynamic();

            int currentPercentage = getCurrentPercentage(control);
            boolean isMachineForced = (control.createLazyTick$getForcedValue() > 0);

            boolean typeMismatch = false;
            if (currentPercentage != 0) {
                if (targetIsDynamic && isMachineForced) typeMismatch = true;
                if (!targetIsDynamic && !isMachineForced) typeMismatch = true;
            }

            int nextPercentage;
            if (typeMismatch) {
                nextPercentage = sequence.get(0);
            } else {
                nextPercentage = getNextPercentage(sequence, currentPercentage);
            }

            control.createLazyTick$setOwnerName(player.getName().getString());
            control.createLazyTick$setOwnerUUID(player.getUUID());

            applyPercentage(control, nextPercentage, targetIsDynamic);
            LazyTickLogic.updateState(control);

            int maxDelayTick = whiteItem.getMaxTick();
            MutableComponent message = Component.translatable("createlazytick.clock.mode_changed");

            List<Component> infoList = LazyTickMode.getDisplayComponents(
                    control.createLazyTick$getDynamicValue(),
                    control.createLazyTick$getForcedValue(),
                    maxDelayTick
            );

            for (Component c : infoList) {
                message.append(c);
            }

            player.displayClientMessage(message, true);
            player.getCooldowns().addCooldown(this, 10);

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private List<Integer> getSafeSequence() {
        List<? extends Integer> rawList = ServerConfig.getClockModeSequence();
        List<Integer> safeList = new ArrayList<>();

        if (rawList != null) {
            for (Integer val : rawList) {
                if (val >= 0 && val <= 100) {
                    safeList.add(val);
                }
            }
        }

        Collections.sort(safeList);

        if (safeList.isEmpty()) {
            safeList.add(0);
        }

        return safeList;
    }

    private int getCurrentPercentage(ISmartBlockEntityControl control) {
        int frc = control.createLazyTick$getForcedValue();
        if (frc != -1) return frc;
        return control.createLazyTick$getDynamicValue();
    }

    private int getNextPercentage(List<Integer> sequence, int current) {
        for (Integer val : sequence) {
            if (val > current) return val;
        }
        return sequence.get(0);
    }

    private void applyPercentage(ISmartBlockEntityControl control, int percent, boolean isDynamicMode) {
        if (percent == 0) {
            LazyTickLogic.switchMode(control, true, 0);
        } else if (isDynamicMode) {
            LazyTickLogic.switchMode(control, false, percent);
        } else {
            LazyTickLogic.switchMode(control, true, percent);
        }

        if (control instanceof SmartBlockEntity be) {
            LazyTickScrollBehaviour behaviour = LazyTickLogic.getBehaviour(be, LazyTickScrollBehaviour.class);

            if (behaviour != null) {
                int targetUiValue;
                if (percent == 0) {
                    targetUiValue = 0;
                } else if (isDynamicMode) {
                    targetUiValue = percent;
                } else {
                    targetUiValue = -percent;
                }
                behaviour.setValue(targetUiValue);
            }
        }
    }
}