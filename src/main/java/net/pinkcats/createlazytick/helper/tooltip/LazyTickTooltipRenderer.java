package net.pinkcats.createlazytick.helper.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.pinkcats.createlazytick.Channel.CLTChannel;
import net.pinkcats.createlazytick.Channel.ClockSyncPacket;
import net.pinkcats.createlazytick.Gui.mes;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;
import net.pinkcats.createlazytick.config.ClientConfig;

import java.util.List;

public class LazyTickTooltipRenderer {

    private static long lastQueryTick = -1;

    public static int appendLazyTickInfo(ISmartBlockEntityControl control, List<Component> tooltip,
                                         int currentTick, int maxDelayTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return currentTick;

        long currentGameTime = mc.level.getGameTime();

        if (currentGameTime % 10 == 0 && currentGameTime != lastQueryTick) {

            lastQueryTick = currentGameTime;

            CLTChannel.sendToServer(new ClockSyncPacket(control.CLT$getPos()));
        }

        int dynamicValue = control.createLazyTick$getDynamicValue();
        int forcedValue = control.createLazyTick$getForcedValue();

        ToolTipStatus(tooltip);

        if (control.createLazyTick$shouldRenderMode() && ClientConfig.showModeTooltip()) {

            tooltip.addAll(LazyTickMode.getDisplayComponents(dynamicValue, forcedValue, maxDelayTick));
        }

        if (control.createLazyTick$shouldRenderTier() && ClientConfig.showTierTooltip()) {

            int currentInterval = control.createLazyTick$getCurrentSuperTick();

            if (currentInterval < 1) currentInterval = 1;
            int limitPercent = (forcedValue > 0) ? forcedValue : dynamicValue;

            tooltip.addAll(control.lazytick$getSyncedTier()
                    .getDisplayComponents(currentInterval, maxDelayTick, limitPercent));
        }

        String op = control.createLazyTick$getOwnerName();
        if (!op.isEmpty()) {

            tooltip.add(Component.translatable("createlazytick.tooltip.operator", op).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (control.createLazyTick$shouldRenderMode() && ClientConfig.showDescriptionTooltip()) {
            tooltip.add(LazyTickMode.getModeDescription(dynamicValue, forcedValue));
        }

        List<Component> customInfo = control.createLazyTick$getCustomTooltipInfo();
        if (customInfo != null && !customInfo.isEmpty()) {

            tooltip.add(mes.spaces(3));
            tooltip.addAll(customInfo);
        }

        return currentTick;
    }

    public static void appendSimpleConfigInfo(Object be, List<Component> tooltip) {
        LazyTickTooltipWhiteList whiteItem = LazyTickTooltipWhiteList.getByEntity(be);
        if (whiteItem != null) {
            ToolTipStatus(tooltip);
            if (whiteItem == LazyTickTooltipWhiteList.PIPE || whiteItem == LazyTickTooltipWhiteList.PUMP) {
                tooltip.add(Component.translatable("createlazytick.tooltip.fluid_system_delay", whiteItem.getMaxTick())
                        .withStyle(ChatFormatting.GRAY));
            } else if (whiteItem == LazyTickTooltipWhiteList.BELT) {
                tooltip.add(Component.translatable( "createlazytick.tooltip.belt_system_delay" , whiteItem.getMaxTick())
                        .withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.translatable("createlazytick.tooltip.global_config_note")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void ToolTipStatus(List<Component> tooltip) {
        if (tooltip.isEmpty()) {
            tooltip.add(mes.spaces(9));
            tooltip.add(Component.translatable("createlazytick.tooltip.status").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(mes.spaces(3));
            tooltip.add(Component.translatable("createlazytick.tooltip.status").withStyle(ChatFormatting.GRAY));
        }
    }
}