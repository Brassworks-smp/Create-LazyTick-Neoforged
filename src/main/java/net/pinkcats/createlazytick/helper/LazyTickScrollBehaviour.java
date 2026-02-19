package net.pinkcats.createlazytick.helper;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.pinkcats.createlazytick.CreateLazyTick;
import net.pinkcats.createlazytick.Gui.mes;
import net.pinkcats.createlazytick.Register.LazyTickItem;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;
import net.pinkcats.createlazytick.helper.tooltip.LazyTickTooltipWhiteList;
import net.pinkcats.createlazytick.helper.util.LazyTickLogic;
import net.pinkcats.createlazytick.manager.ForcedActiveManager;

import java.util.List;

public class LazyTickScrollBehaviour extends ScrollValueBehaviour {

    public LazyTickScrollBehaviour(Component label, SmartBlockEntity be) {
        super(label, be, new InvisibleSlot());
    }

    public static void addTo(SmartBlockEntity be, List<BlockEntityBehaviour> behaviours) {

        if (!(be instanceof ISmartBlockEntityControl control)) {
            return;
        }

        LazyTickTooltipWhiteList whiteItem = LazyTickTooltipWhiteList.getByEntity(be);
        if (whiteItem == null) {
            return;
        }

        LazyTickScrollBehaviour behaviour = new LazyTickScrollBehaviour(Component.translatable("createlazytick.scroll.config"), be);

        behaviour.between(-100, 100);

        if (CreateLazyTick.isClient()) {

            ClientSetup.configureActiveCondition(behaviour);
        }

        behaviour.withCallback(i -> {
            if (i > 0) {

                LazyTickLogic.switchMode(control, false, i);
            } else if (i < 0) {

                LazyTickLogic.switchMode(control, true, Math.abs(i));
            } else {

                LazyTickLogic.switchMode(control, true, 0);
            }
            LazyTickLogic.updateState(control);
        });

        int dynamicValue = control.createLazyTick$getDynamicValue();
        int forcedValue = control.createLazyTick$getForcedValue();

        if (dynamicValue > 0) {
            behaviour.setValue(dynamicValue);
        } else if (forcedValue > 0) {
            behaviour.setValue(-forcedValue);
        } else {
            behaviour.setValue(0);
        }

        behaviours.add(behaviour);
    }

    private static class ClientSetup {
        static void configureActiveCondition(LazyTickScrollBehaviour behaviour) {
            behaviour.onlyActiveWhen(() -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                Player player = mc.player;
                return player != null && player.getMainHandItem().getItem() == LazyTickItem.CLOCK.get();
            });
        }
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {

        return new ValueSettingsBoard(
                label,
                100,
                10,
                ImmutableList.of(
                        Component.translatable("createlazytick.scroll.dynamic_control"), 
                        Component.translatable("createlazytick.scroll.forced_control")  
                ),
                new ValueSettingsFormatter(this::formatDualSettings)
        );
    }

    public MutableComponent formatDualSettings(ValueSettingsBehaviour.ValueSettings settings) {
        if (settings.value() == 0) return Component.translatable("createlazytick.scroll.forced_active");
        return mes.CharM(settings.value() + "%");
    }

    @Override
    public ValueSettings getValueSettings() {

        int row = value >= 0 ? 0 : 1;
        int displayValue = Math.abs(value);
        return new ValueSettings(row, displayValue);
    }

    @Override
    public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
        if (!ForcedActiveManager.canPlayerActivate(blockEntity, player)) {
            return; 
        }

        if (blockEntity instanceof ISmartBlockEntityControl control) {
            control.createLazyTick$setOwnerName(player.getName().getString());
            control.createLazyTick$setOwnerUUID(player.getUUID());
        }

        int newValue = settings.value();

        if (settings.row() == 1) {
            newValue = -newValue;
        }

        setValue(newValue);
        playFeedbackSound(this);
    }

    @Override
    public boolean testHit(Vec3 hit) {
        if (!isActive()) return false;
        Vec3 localHit = hit.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
        boolean insideBlock = localHit.x >= 0 && localHit.x <= 1 &&
                localHit.y >= 0 && localHit.y <= 1 &&
                localHit.z >= 0 && localHit.z <= 1;
        if (!insideBlock) return false;
        return localHit.y < 0.3;
    }

    private static class InvisibleSlot extends ValueBoxTransform.Sided {
        @Override protected Vec3 getSouthLocation() { return Vec3.ZERO; }
        @Override protected boolean isSideActive(BlockState s, Direction d) { return true; }
        @Override public float getScale() { return 0f; }
    }
}