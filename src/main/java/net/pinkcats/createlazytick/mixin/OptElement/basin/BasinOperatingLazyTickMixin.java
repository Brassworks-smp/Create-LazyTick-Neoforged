package net.pinkcats.createlazytick.mixin.OptElement.basin;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.world.item.crafting.Recipe;
import net.pinkcats.createlazytick.config.ServerConfig;
import net.pinkcats.createlazytick.bridge.Basin.IBasinOptimization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Mixin(BasinOperatingBlockEntity.class)
public abstract class BasinOperatingLazyTickMixin {

    @Shadow(remap = false)
    protected abstract Optional<BasinBlockEntity> getBasin();

    @Unique private long cachedBasinVersion = -1;
    @Unique private BlazeBurnerBlock.HeatLevel cachedHeatLevel = BlazeBurnerBlock.HeatLevel.NONE;
    @Unique private List<Recipe<?>> cachedRecipes = null;
    @Unique private BasinBlockEntity cachedBasinRef = null;

    @Inject(method = "getMatchingRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetMatchingRecipes(CallbackInfoReturnable<List<Recipe<?>>> cir) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyBasin()) return;

        Optional<BasinBlockEntity> basinOpt = getBasin();

        if (basinOpt.isEmpty()) {
            return;
        }

        BasinBlockEntity basin = basinOpt.get();

        if (basin.isEmpty()) return;

        IBasinOptimization optimizedBasin = (IBasinOptimization) basin;

        long currentVersion = optimizedBasin.getInventoryVersion();

        BlazeBurnerBlock.HeatLevel currentHeat = optimizedBasin.optimization$getHeatLevel();

        if (cachedRecipes != null &&
                cachedBasinRef == basin &&
                currentVersion == cachedBasinVersion &&
                currentHeat == cachedHeatLevel) {

            cir.setReturnValue(cachedRecipes);
            cir.cancel();
        }
    }

    @Inject(method = "getMatchingRecipes", at = @At("RETURN"), remap = false)
    private void captureMatchingRecipes(CallbackInfoReturnable<List<Recipe<?>>> cir) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyBasin()) return;

        Optional<BasinBlockEntity> basinOpt = getBasin();
        if (basinOpt.isPresent()) {
            BasinBlockEntity basin = basinOpt.get();
            IBasinOptimization optimizedBasin = (IBasinOptimization) basin;

            this.cachedBasinRef = basin;
            this.cachedBasinVersion = optimizedBasin.getInventoryVersion();
            this.cachedHeatLevel = optimizedBasin.optimization$getHeatLevel();

            List<Recipe<?>> ret = cir.getReturnValue();
            this.cachedRecipes = (ret == null) ? Collections.emptyList() : ret;
        } else {
            this.cachedRecipes = null;
            this.cachedBasinRef = null;
            this.cachedBasinVersion = -1;
            this.cachedHeatLevel = BlazeBurnerBlock.HeatLevel.NONE;
        }
    }
}