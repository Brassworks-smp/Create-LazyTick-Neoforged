package net.pinkcats.createlazytick.mixin.OptElement;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.pinkcats.createlazytick.helper.util.RegistriesWrapper;
import net.pinkcats.createlazytick.config.ServerConfig;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;
import net.pinkcats.createlazytick.helper.util.LazyTickLogic;
import net.pinkcats.createlazytick.helper.LazyTickScrollBehaviour;
import net.pinkcats.createlazytick.helper.NetworkSyncHelper;
import net.pinkcats.createlazytick.helper.util.ScheduleTicker;
import net.pinkcats.createlazytick.helper.extraDataTool.ArmExtraDataTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.pinkcats.createlazytick.CreateLazyTick.DropResourceLocation;
import static net.pinkcats.createlazytick.CreateLazyTick.IsServerReload;

@Mixin(ArmBlockEntity.class)
public abstract class ArmLazyTickMixin extends SmartBlockEntity implements ISmartBlockEntityControl {

    @Shadow(remap = false)
    List<ArmInteractionPoint> inputs;
    @Shadow(remap = false)
    List<ArmInteractionPoint> outputs;
    @Shadow(remap = false)
    ArmBlockEntity.Phase phase;

    @Unique
    private int createLazyTick$armTick = 0;

    @Unique
    private boolean createLazyTick$ignoreLazy = false; 
    @Unique
    private boolean createLazyTick$weakLazy = false;   

    @Unique
    private static final int REVALIDATE_INTERVAL = 200;

    @Unique private static Set<Block> createLazyTick$cachedIgnoreBlocks = null;
    @Unique private static Set<Block> createLazyTick$cachedWeakBlocks = null;

    public ArmLazyTickMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private void createLazyTick$rebuildCaches() {
        createLazyTick$cachedIgnoreBlocks = new HashSet<>();
        createLazyTick$cachedWeakBlocks = new HashSet<>();

        for (String id : ServerConfig.getArmIgnoreLazytickList()) {
            ResourceLocation loc = DropResourceLocation(id);
            if (RegistriesWrapper.BLOCKS.containsKey(loc)) {
                createLazyTick$cachedIgnoreBlocks.add(RegistriesWrapper.BLOCKS.getValue(loc));
            }
        }

        for (String id : ServerConfig.getArmWeakLazytickList()) {
            ResourceLocation loc = DropResourceLocation(id);
            if (RegistriesWrapper.BLOCKS.containsKey(loc)) {
                createLazyTick$cachedWeakBlocks.add(RegistriesWrapper.BLOCKS.getValue(loc));
            }
        }

    }

    @Unique
    private void createLazyTick$ensureConfigCaches() {

        if (IsServerReload) {
            return;
        }

        if (createLazyTick$cachedIgnoreBlocks == null) {
            createLazyTick$rebuildCaches();
        }
    }

    @Unique
    private boolean createLazyTick$isBlockInConfig(Block block, Set<Block> configSet) {
        if (block == null || configSet == null || configSet.isEmpty()) return false;
        return configSet.contains(block);
    }

    @Unique
    private void createLazyTick$scanUrgency() {
        if (level == null || level.isClientSide) return;

        createLazyTick$ensureConfigCaches();

        boolean foundIgnore = false;
        boolean foundWeak = false;

        if (inputs != null) {
            for (ArmInteractionPoint point : inputs) {
                if (point == null) continue;

                BlockState state = level.getBlockState(point.getPos());
                Block block = state.getBlock();

                if (createLazyTick$isBlockInConfig(block, createLazyTick$cachedIgnoreBlocks)) {
                    foundIgnore = true;
                    break; 
                }
                if (createLazyTick$isBlockInConfig(block, createLazyTick$cachedWeakBlocks)) {
                    foundWeak = true;
                }
            }
        }

        if (!foundIgnore && outputs != null) {
            for (ArmInteractionPoint point : outputs) {

                if (point == null) continue;
                BlockState state = level.getBlockState(point.getPos());
                Block block = state.getBlock();

                if (createLazyTick$isBlockInConfig(block, createLazyTick$cachedIgnoreBlocks)) {
                    foundIgnore = true;
                    break;
                }
                if (createLazyTick$isBlockInConfig(block, createLazyTick$cachedWeakBlocks)) {
                    foundWeak = true;
                }
            }
        }

        boolean oldIgnore = this.createLazyTick$ignoreLazy;
        boolean oldWeak = this.createLazyTick$weakLazy;

        this.createLazyTick$ignoreLazy = foundIgnore;
        this.createLazyTick$weakLazy = foundWeak;

        if ((foundIgnore && !oldIgnore) || (foundWeak && !oldWeak)) {
            this.createLazyTick$resetDelayTick();
        }
    }

    @Unique
    private final ScheduleTicker ScanBlockType_Schedule = new ScheduleTicker(REVALIDATE_INTERVAL, this::createLazyTick$scanUrgency);

    @Unique
    private void createLazyTick$resetDelayTick() {
        createLazyTick$armTick = 0;

        LazyTickLogic.setIntervalSafe(this, 1);
    }

    @Inject(method = "initInteractionPoints", at = @At("RETURN"), remap = false)
    private void createLazyTick$onInitPoints(CallbackInfo ci) {
        createLazyTick$scanUrgency();
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void createLazyTick$tickCheck(CallbackInfo ci) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyArm()) return;
        if (level == null || level.isClientSide) return;

        NetworkSyncHelper.createLazyTick$syncPacketData(this,
                this.level, this.worldPosition, this.createLazyTick$getCurrentSuperTick(), ServerConfig.getArmDelayMax());

        ScanBlockType_Schedule.RandomTick();

        this.lazytick$setExtraData(ArmExtraDataTool.packArmData(createLazyTick$ignoreLazy, createLazyTick$weakLazy));

    }

    @Inject(method = "addBehaviours", at = @At("RETURN"), remap = false)
    private void lazytick$addScrollBehaviour(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        LazyTickScrollBehaviour.addTo(this, behaviours);
    }

    @Inject(method = "searchForItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void createLazyTick$searchForItemHead(CallbackInfo ci) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyArm()) return;

        if (createLazyTick$ignoreLazy) {
            this.createLazyTick$setCurrentSuperTick(1);
            return;
        }

        createLazyTick$armTick++;
        if (createLazyTick$armTick < this.createLazyTick$getCurrentSuperTick()) {
            ci.cancel();
        } else {
            createLazyTick$armTick = 0;
        }
    }

    @Inject(method = "searchForItem", at = @At("RETURN"), remap = false)
    private void createLazyTick$searchForItemReturn(CallbackInfo ci) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyArm()) return;
        if (createLazyTick$ignoreLazy) return;

        if (this.phase != ArmBlockEntity.Phase.SEARCH_INPUTS) {

            createLazyTick$resetDelayTick();
        } else {

            int maxDelay = ServerConfig.getArmDelayMax();
            if (createLazyTick$weakLazy) {
                maxDelay = Math.min(ServerConfig.getArmWeakDelayMax(), maxDelay);
            }

            int currentInterval = this.createLazyTick$getCurrentSuperTick();

            if (currentInterval < maxDelay) {
                int newInterval = LazyTickLogic.computeNextInterval(this,currentInterval,maxDelay);
                if (newInterval != currentInterval) {
                    LazyTickLogic.setIntervalSafe(this, newInterval);
                }
            }
        }
    }

    @Inject(method = "searchForDestination", at = @At("HEAD"), cancellable = true, remap = false)
    private void createLazyTick$searchForDestinationHead(CallbackInfo ci) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyArm()) return;

        if (createLazyTick$ignoreLazy) {
            this.createLazyTick$setCurrentSuperTick(1);
            return;
        }

        createLazyTick$armTick++;
        if (createLazyTick$armTick < this.createLazyTick$getCurrentSuperTick()) {
            ci.cancel();
        } else {
            createLazyTick$armTick = 0;
        }
    }

    @Inject(method = "searchForDestination", at = @At("RETURN"), remap = false)
    private void createLazyTick$searchForDestinationReturn(CallbackInfo ci) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyArm()) return;
        if (createLazyTick$ignoreLazy) return;

        if (this.phase != ArmBlockEntity.Phase.SEARCH_OUTPUTS) {

            createLazyTick$resetDelayTick();
        } else {

            int maxDelay = ServerConfig.getArmDelayMax();
            if (createLazyTick$weakLazy) {
                maxDelay = Math.min(ServerConfig.getArmWeakDelayMax(), maxDelay);
            }

            int currentInterval = this.createLazyTick$getCurrentSuperTick();

            if (currentInterval < maxDelay) {
                int newInterval = LazyTickLogic.computeNextInterval(this,currentInterval,maxDelay);
                if (newInterval != currentInterval) {
                    LazyTickLogic.setIntervalSafe(this, newInterval);
                }
            }
        }
    }

    @Override
    public List<Component> createLazyTick$getCustomTooltipInfo() {
        List<Component> tooltip = new ArrayList<>();

        int data = this.lazytick$getExtraData();
        boolean ignore = ArmExtraDataTool.unpackIgnore(data);
        boolean weak = ArmExtraDataTool.unpackWeak(data);

        if (ignore) {
            tooltip.add(Component.translatable("createlazytick.arm.config_exemption_full_speed").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("createlazytick.arm.ignore_list_disabled").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("createlazytick.arm.cannot_change_interval").withStyle(ChatFormatting.GOLD));
        } else if (weak) {
            tooltip.add(Component.translatable("createlazytick.arm.config_exemption_weak").withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("createlazytick.arm.weak_list_shortened").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("createlazytick.arm.can_change_by_force").withStyle(ChatFormatting.GRAY));
        }

        return tooltip;
    }

    @Override
    public boolean createLazyTick$shouldRenderTier() {
        int data = this.lazytick$getExtraData();
        boolean ignore = ArmExtraDataTool.unpackIgnore(data);
        return !ignore;
    }

    @Override
    public boolean createLazyTick$shouldRenderMode() {
        int data = this.lazytick$getExtraData();
        boolean ignore = ArmExtraDataTool.unpackIgnore(data);
        return !ignore;
    }
}