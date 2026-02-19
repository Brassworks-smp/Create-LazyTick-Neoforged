package net.pinkcats.createlazytick.mixin.OptElement.crafter;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.pinkcats.createlazytick.config.ServerConfig;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;
import net.pinkcats.createlazytick.helper.util.LazyTickLogic;
import net.pinkcats.createlazytick.helper.LazyTickScrollBehaviour;
import net.pinkcats.createlazytick.helper.NetworkSyncHelper;
import net.pinkcats.createlazytick.helper.util.ScheduleTicker;
import net.pinkcats.createlazytick.helper.extraDataTool.CrafterExtraDataTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static net.pinkcats.createlazytick.CreateLazyTick.IsServerReload;
import static net.pinkcats.createlazytick.helper.extraDataTool.CrafterExtraDataTool.packCrafterData;

@Mixin(value = MechanicalCrafterBlockEntity.class,remap = false)
public abstract class CrafterRedstoneLazyTickMixin extends SmartBlockEntity implements ISmartBlockEntityControl {

    @Shadow(remap = false)
    protected boolean wasPoweredBefore;

    @Unique
    private int lazytick$redstoneTick = 0;
    @Unique
    private long lazytick$lastActiveTime = -1;

    @Unique
    private boolean lazytick$cachedSignal = false;

    @Unique
    private static final int WINDOW_POWERED = 2400;

    @Unique
    private static final int WINDOW_UNPOWERED = 200;

    public CrafterRedstoneLazyTickMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private void lazytick$lowFrequencySync() {
        if (level == null) return;
        long time = level.getGameTime();
        boolean isPowered = this.lazytick$cachedSignal;

        boolean lazytick$cachedInWindow = lazytick$isInWindow(time, isPowered);

        boolean isDelayForced = this.createLazyTick$isDelayForced();
        this.lazytick$setExtraData(packCrafterData(isPowered, lazytick$cachedInWindow, isDelayForced));
    }

    @Unique
    private final ScheduleTicker LowFreq_Schedule = new ScheduleTicker(10, this::lazytick$lowFrequencySync);

    @Unique
    private boolean lazytick$isInWindow(long gameTime, boolean isPowered) {

        int currentWindow = isPowered ? WINDOW_POWERED : WINDOW_UNPOWERED;
        return (gameTime - lazytick$lastActiveTime < currentWindow);
    }

    @Unique
    private void lazytick$updateInterval(boolean signalChanged, boolean isPowered, long gameTime) {
        int maxDelay = ServerConfig.getCrafterRedstoneDelayMax();

        if (signalChanged) {
            lazytick$lastActiveTime = gameTime;
            LazyTickLogic.setIntervalSafe(this,1);
            return;
        }

        if (lazytick$isInWindow(gameTime, isPowered)) {
            LazyTickLogic.setIntervalSafe(this,1);
            return;
        }

        int currentInterval = this.createLazyTick$getCurrentSuperTick();
        if (currentInterval < maxDelay) {
            int newDelayTick = LazyTickLogic.computeNextInterval(this, currentInterval, maxDelay);

            if (newDelayTick != currentInterval) {
                LazyTickLogic.setIntervalSafe(this, newDelayTick);
            }
        }
    }

    @Inject(method = "addBehaviours", at = @At("RETURN"), remap = false)
    private void lazytick$addScrollBehaviour(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        LazyTickScrollBehaviour.addTo(this, behaviours);
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void lazytick$onTickHead(CallbackInfo ci) {
        if (level == null || level.isClientSide) return;

        LowFreq_Schedule.RandomTick();

        NetworkSyncHelper.createLazyTick$syncPacketData(this,
                this.level, this.worldPosition, this.createLazyTick$getCurrentSuperTick(), ServerConfig.getCrafterRedstoneDelayMax());

    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;hasNeighborSignal(Lnet/minecraft/core/BlockPos;)Z"
            ),
            remap = true
    )
    private boolean lazytick$dynamicRedstoneCheck(Level level, BlockPos pos) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyCrafterRedstone() || level.isClientSide) {

            return level.hasNeighborSignal(pos);
        }

        int interval = this.createLazyTick$getCurrentSuperTick();
        if (interval > 1) {
            lazytick$redstoneTick++;
            if (lazytick$redstoneTick < interval) {
                return this.lazytick$cachedSignal;
            }
            lazytick$redstoneTick = 0;
        }

        long gameTime = level.getGameTime();

        if (IsServerReload) {
            lazytick$lastActiveTime = gameTime;
            LazyTickLogic.setIntervalSafe(this,1);
            lazytick$redstoneTick = 0;
            return level.hasNeighborSignal(pos);
        }

        boolean realSignal = level.hasNeighborSignal(pos);

        this.lazytick$cachedSignal = realSignal;

        boolean changed = (realSignal != this.wasPoweredBefore);

        lazytick$updateInterval(changed, realSignal, gameTime);

        return realSignal;
    }

    @Override
    public List<Component> createLazyTick$getCustomTooltipInfo() {
        List<Component> tooltip = new ArrayList<>();

        int data = this.lazytick$getExtraData();
        boolean isPowered = CrafterExtraDataTool.unpackIsPowered(data);
        boolean isInWindow = CrafterExtraDataTool.unpackInWindow(data);
        boolean isDelayForced = CrafterExtraDataTool.unpackIsDelayForced(data);

        if (isPowered) {
            tooltip.add(Component.translatable("createlazytick.crafter.redstone_powered").withStyle(ChatFormatting.RED));
            if (!isDelayForced) {
                if (isInWindow) {
                    tooltip.add(Component.translatable("createlazytick.crafter.full_speed_window").withStyle(ChatFormatting.GREEN));
                } else {
                    tooltip.add(Component.translatable("createlazytick.crafter.idle_too_long").withStyle(ChatFormatting.RED));
                }
            } else {
                tooltip.add(Component.translatable("createlazytick.crafter.forced_control").withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("createlazytick.crafter.redstone_unpowered").withStyle(ChatFormatting.DARK_GRAY));
            if (isInWindow) {
                if (isDelayForced) {
                    tooltip.add(Component.translatable("createlazytick.crafter.forced_control").withStyle(ChatFormatting.GRAY));
                } else {
                    tooltip.add(Component.translatable("createlazytick.crafter.fast_speed_window").withStyle(ChatFormatting.GREEN));
                }
            }
        }

        tooltip.add(Component.translatable("createlazytick.crafter.no_delay_full_slots").withStyle(ChatFormatting.GRAY));
        return tooltip;
    }
}