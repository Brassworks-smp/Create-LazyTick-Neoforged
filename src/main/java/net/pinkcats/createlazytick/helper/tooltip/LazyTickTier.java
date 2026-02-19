package net.pinkcats.createlazytick.helper.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.pinkcats.createlazytick.Gui.mes;
import net.pinkcats.createlazytick.config.ClientConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum LazyTickTier {
    ACTIVE(ChatFormatting.GREEN),
    LIGHT(ChatFormatting.YELLOW),
    MEDIUM(ChatFormatting.GOLD),
    DEEP(ChatFormatting.RED);

    public final ChatFormatting color;

    private static final int BAR_COUNT = 34;

    LazyTickTier(ChatFormatting color) {
        this.color = color;
    }

    public List<Component> getDisplayComponents(int currentInterval, int maxTick, int limitPercent) {
        if (maxTick < 1) maxTick = 1;

        MutableComponent bar = createBarComponent(currentInterval, maxTick, limitPercent);

        MutableComponent stats = createStatsComponent(currentInterval, maxTick);

        return renderByConfig(bar, stats);
    }

    private List<Component> renderByConfig(MutableComponent bar, MutableComponent stats) {
        ClientConfig.TierFormat format = ClientConfig.getTierFormat();
        List<Component> list = new ArrayList<>();

        if (format == ClientConfig.TierFormat.BAR || format == ClientConfig.TierFormat.BOTH) {
            list.add(bar);
        }

        if (format == ClientConfig.TierFormat.NUMBER || format == ClientConfig.TierFormat.BOTH) {
            if (format == ClientConfig.TierFormat.BOTH) {
                MutableComponent b2 = (MutableComponent) mes.spaces(1);
                list.add(b2.append(stats));
            } else {

                list.add(stats);
            }
        }

        if (list.isEmpty()) {
            list.add(bar);
        }

        return list;
    }

    private MutableComponent createBarComponent(int currentInterval, int maxTick, int limitPercent) {

        boolean isActiveMachine = maxTick <= 2;

        boolean isLowLoad = currentInterval <= 3;

        int filledBars;

        if (isActiveMachine) {

            filledBars = 1;
        } else {

            float percent = (float) currentInterval / maxTick * 100f;

            if (percent >= 99.1f) {
                filledBars = 34; 
            } else {
                filledBars = (int) Math.ceil(percent / 3.0f); 
            }

            if (filledBars > BAR_COUNT) filledBars = BAR_COUNT;

            if (filledBars == 0 && currentInterval > 0) filledBars = 1;
        }

        int limitIndex;
        if (limitPercent >= 100) {
            limitIndex = 33;
        } else if (limitPercent <= 0) {
            limitIndex = 0;
        } else {
            limitIndex = limitPercent / 3;
        }

        MutableComponent bar = mes.CharM(" [").withStyle(ChatFormatting.GRAY);

        for (int i = 0; i < BAR_COUNT; i++) {
            ChatFormatting barColor;

            if (i == limitIndex) {
                barColor = ChatFormatting.DARK_PURPLE;
            }

            else if (i < filledBars) {
                if (isActiveMachine || isLowLoad) {

                    barColor = ChatFormatting.GREEN;
                } else {

                    float progress = (float) i / BAR_COUNT;

                    if (progress < 0.40f) {
                        barColor = ChatFormatting.YELLOW; 
                    } else if (progress < 0.70f) {
                        barColor = ChatFormatting.GOLD;   
                    } else {
                        barColor = ChatFormatting.RED;    
                    }
                }
            }

            else {
                barColor = ChatFormatting.DARK_GRAY;
            }
            bar.append(mes.CharM("|").withStyle(barColor));
        }

        bar.append(mes.CharM("] ").withStyle(ChatFormatting.GRAY));

        return bar;
    }

    public MutableComponent createStatsComponent(int currentInterval, int maxTick) {
        String currStr = LazyTickTooltipTool.formatTime(currentInterval);
        String maxStr = LazyTickTooltipTool.formatTime(maxTick);

        return mes.CharM(String.format("(%s / %s)", currStr, maxStr)).withStyle(ChatFormatting.GRAY);
    }

    public static LazyTickTier fromTicks(int currentInterval, int maxTick) {
        if (maxTick < 1) maxTick = 1;

        if (maxTick <= 2) return ACTIVE;

        if (currentInterval <= 3) return ACTIVE;

        if (currentInterval >= maxTick) {
            return DEEP;
        }

        float percent = (float) currentInterval / maxTick;

        if (percent < 0.40f) return LIGHT;   
        if (percent < 0.70f) return MEDIUM;  
        return DEEP;
    }
}