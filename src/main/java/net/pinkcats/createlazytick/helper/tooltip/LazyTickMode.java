package net.pinkcats.createlazytick.helper.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.pinkcats.createlazytick.Gui.mes;
import net.pinkcats.createlazytick.config.ClientConfig;

import java.util.ArrayList;
import java.util.List;

public enum LazyTickMode {
    AUTO_SLEEP_LIGHT("createlazytick.mode.auto_sleep.light", ChatFormatting.YELLOW, false),
    AUTO_SLEEP_MEDIUM("createlazytick.mode.auto_sleep.medium", ChatFormatting.GOLD, false),
    AUTO_SLEEP_DEEP("createlazytick.mode.auto_sleep.deep", ChatFormatting.RED, false),
    AUTO_SLEEP_DEFAULT("createlazytick.mode.auto_sleep.default", ChatFormatting.DARK_GRAY, false),

    FORCED_FULL("createlazytick.mode.forced.full", ChatFormatting.DARK_PURPLE, true),
    FORCED_SLEEP_LIGHT("createlazytick.mode.forced.sleep_light", ChatFormatting.YELLOW, true),
    FORCED_SLEEP_MEDIUM("createlazytick.mode.forced.sleep_medium", ChatFormatting.GOLD, true),
    FORCED_SLEEP_DEEP("createlazytick.mode.forced.sleep_deep", ChatFormatting.RED, true);

    private final String key;
    private final ChatFormatting color;
    private final boolean isBold;
    LazyTickMode(String key, ChatFormatting color, boolean isBold) {
        this.key = key;
        this.color = color;
        this.isBold = isBold;
    }

    public static List<Component> getDisplayComponents(int dynamicValue, int forcedValue, int maxTick) {

        LazyTickMode mode = resolveMode(dynamicValue, forcedValue);

        if (mode == null) {
            return List.of(Component.translatable("createlazytick.mode.unknown").withStyle(ChatFormatting.DARK_RED));
        }

        MutableComponent extraInfo = generateExtraInfo(dynamicValue, forcedValue, maxTick);

        return renderByConfig(mode, extraInfo);
    }

    private static LazyTickMode resolveMode(int dynamicValue, int forcedValue) {

        if (forcedValue != -1) {
            if (forcedValue == 0) return FORCED_FULL;
            return getTierMode(forcedValue, true); 
        }

        if (dynamicValue > 0) {
            if (dynamicValue == 100) return AUTO_SLEEP_DEFAULT;
            return getTierMode(dynamicValue, false); 
        }

        return null; 
    }

    private static LazyTickMode getTierMode(int value, boolean isForced) {
        if (value <= 30) return isForced ? FORCED_SLEEP_LIGHT : AUTO_SLEEP_LIGHT;
        if (value <= 70) return isForced ? FORCED_SLEEP_MEDIUM : AUTO_SLEEP_MEDIUM;
        return isForced ? FORCED_SLEEP_DEEP : AUTO_SLEEP_DEEP;
    }

    private static MutableComponent generateExtraInfo(int dynamicValue, int forcedValue, int maxTick) {
        boolean isForced = (forcedValue != -1);
        int percentage = isForced ? forcedValue : dynamicValue;

        int actualInterval = Math.max(1, maxTick * percentage / 100);

        String timeStr = LazyTickTooltipTool.formatTime(actualInterval);

        if (isForced) {
            return Component.translatable(
                    "createlazytick.tooltip.extra.fixed",
                    percentage,
                    timeStr
            ).withStyle(ChatFormatting.GRAY);
        } else {
            return Component.translatable(
                    "createlazytick.tooltip.extra.dynamic",
                    percentage,
                    timeStr
            ).withStyle(ChatFormatting.GRAY);

        }
    }

    private MutableComponent getBaseComponent() {
        MutableComponent base = Component.translatable(key); 
        base = base.withStyle(color);
        if (isBold) base = base.withStyle(style -> style.withBold(true));
        return base;
    }

    private static List<Component> renderByConfig(LazyTickMode mode, MutableComponent extraInfo) {
        List<Component> list = new ArrayList<>();

        ClientConfig.ModeFormat format = ClientConfig.getModeFormat();

        if (format == ClientConfig.ModeFormat.TEXT || format == ClientConfig.ModeFormat.BOTH) {
            list.add(mode.getBaseComponent());
        }

        if (format == ClientConfig.ModeFormat.NUMBER || format == ClientConfig.ModeFormat.BOTH) {
            if (extraInfo != null) {
                MutableComponent a = (MutableComponent) mes.spaces(1);
                list.add(a.append(extraInfo));
            }
        }

        if (list.isEmpty()) {
            list.add(mode.getBaseComponent());
        }

        return list;
    }

    public static Component getModeDescription(int dynamicValue, int forcedValue) {

        if (forcedValue != -1) {
            if (forcedValue == 0) {
                return Component.translatable("createlazytick.mode.description.forced_full")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            }
            return Component.translatable("createlazytick.mode.description.forced_sleep")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        }

        if (dynamicValue > 0) {
            if (dynamicValue == 100) {
                return Component.translatable("createlazytick.mode.description.auto_default")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            }
            return Component.translatable("createlazytick.mode.description.auto_dynamic")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        }

        return Component.translatable("createlazytick.mode.description.unknown_bug")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC);
    }
}