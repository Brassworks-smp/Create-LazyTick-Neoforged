package net.pinkcats.createlazytick.mixin.OptElement.basin;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.pinkcats.createlazytick.config.ServerConfig;
import net.pinkcats.createlazytick.bridge.Basin.IBasinOptimization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BasinBlockEntity.class,remap = false)
public abstract class BasinLazyTickMixin extends SmartBlockEntity implements IBasinOptimization {

    @Deprecated
    @Shadow
    private boolean contentsChanged; 

    @Unique
    private long optimization$inventoryVersion = 0;

    @Unique
    private BlazeBurnerBlock.HeatLevel lazytick$cachedHeatLevel = null;

    public BasinLazyTickMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public long getInventoryVersion() {
        return optimization$inventoryVersion;
    }

    @Override
    public BlazeBurnerBlock.HeatLevel optimization$getHeatLevel() {
        if (lazytick$cachedHeatLevel == null) {
            if (level == null) return BlazeBurnerBlock.HeatLevel.NONE;
            BlockState stateBelow = level.getBlockState(worldPosition.below());
            lazytick$cachedHeatLevel = BlazeBurnerBlock.getHeatLevelOf(stateBelow);
        }
        return lazytick$cachedHeatLevel;
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void clt$onTick(CallbackInfo ci) {

        lazytick$cachedHeatLevel = null;
    }

    @Inject(method = "notifyChangeOfContents", at = @At("HEAD"), remap = false)
    private void clt$onNotifyChange(CallbackInfo ci) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyBasin()) return;
        optimization$inventoryVersion++;
    }
}