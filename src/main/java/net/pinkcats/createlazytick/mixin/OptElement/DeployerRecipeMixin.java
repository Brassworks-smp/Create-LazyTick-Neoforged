package net.pinkcats.createlazytick.mixin.OptElement;

import com.simibubi.create.content.equipment.sandPaper.SandPaperItem;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.pinkcats.createlazytick.helper.util.RegistriesWrapper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.pinkcats.createlazytick.config.ServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.pinkcats.createlazytick.CreateLazyTick.DropResourceLocation;
import static net.pinkcats.createlazytick.CreateLazyTick.IsServerReload;

@Mixin(DeployerBlockEntity.class)
public abstract class DeployerRecipeMixin {

    @Shadow(remap = false)
    public abstract DeployerFakePlayer getPlayer();

    @Shadow(remap = false)
    ItemStackHandler recipeInv;

    @Unique private boolean lazytick$hasCached = false;
    @Unique private Item lazytick$cachedTargetItem = null;
    @Unique private Item lazytick$cachedHeldItem = null;

    @Unique private RecipeHolder<?> lazytick$cachedRecipe = null;
    @Unique private boolean lazytick$isBlacklisted = false;

    @Unique
    private static final ResourceLocation CREATE_SEQUENCED_ASSEMBLY = DropResourceLocation("create", "sequenced_assembly");

    @Unique
    private boolean lazytick$isDangerousRecipe(RecipeHolder<?> recipeHolder) {
        if (recipeHolder == null) return false;

        Recipe<?> recipe = recipeHolder.value();

        try {
            @SuppressWarnings("ConstantConditions")
            DeployerBlockEntity be = (DeployerBlockEntity)(Object)this;
            Level level = be.getLevel();
            if (level == null) return true;

            RegistryAccess access = level.registryAccess();
            ItemStack resultStack = recipe.getResultItem(access);

            if (!resultStack.isEmpty()) {
                Item resultItem = resultStack.getItem();

                if (resultItem instanceof SequencedAssemblyItem) {
                    return true;
                }

                ResourceLocation itemId = RegistriesWrapper.ITEMS.getKey(resultItem);
                if (itemId != null && itemId.getPath().contains("incomplete")) {
                    return true;
                }
            }
        } catch (Exception e) {
            return true;
        }

        if (recipe instanceof SequencedAssemblyRecipe) return true;

        try {
            RecipeSerializer<?> serializer = recipe.getSerializer();
            ResourceLocation serializerId = RegistriesWrapper.RECIPE_SERIALIZERS.getKey(serializer);
            if (CREATE_SEQUENCED_ASSEMBLY.equals(serializerId)) return true;
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    @Inject(method = "getRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void lazytick$checkCache(ItemStack stack, CallbackInfoReturnable<RecipeHolder<?>> cir) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableCacheDeployer()) return;

        if (IsServerReload) {
            this.lazytick$clearCache();
        }

        if (!lazytick$hasCached) return;

        if (stack.getItem() != lazytick$cachedTargetItem) return;

        if (lazytick$isBlacklisted) {
            return;
        }

        DeployerFakePlayer player = this.getPlayer();
        if (player == null) return;
        ItemStack currentHeld = player.getMainHandItem();

        if (currentHeld.getItem() != lazytick$cachedHeldItem) return;

        if (lazytick$isDangerousRecipe(lazytick$cachedRecipe)) {
            this.lazytick$clearCache();
            return;
        }

        if (this.recipeInv != null) {
            this.recipeInv.setStackInSlot(0, stack);
            this.recipeInv.setStackInSlot(1, currentHeld);
        }

        cir.setReturnValue(lazytick$cachedRecipe);
        cir.cancel();
    }

    @Inject(method = "getRecipe", at = @At("RETURN"), remap = false)
    private void lazytick$saveCache(ItemStack stack, CallbackInfoReturnable<RecipeHolder<?>> cir) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableCacheDeployer()) return;

        DeployerFakePlayer player = this.getPlayer();
        if (player == null) return;

        RecipeHolder<?> result = cir.getReturnValue();

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        boolean inputIsSequenced = customData.contains("SequencedAssembly");

        boolean recipeIsDangerous = lazytick$isDangerousRecipe(result);

        if (inputIsSequenced || recipeIsDangerous) {
            this.lazytick$hasCached = true;
            this.lazytick$cachedTargetItem = stack.getItem();
            this.lazytick$cachedHeldItem = player.getMainHandItem().getItem();

            this.lazytick$isBlacklisted = true;
            this.lazytick$cachedRecipe = null;
            return;
        }

        this.lazytick$hasCached = true;
        this.lazytick$cachedTargetItem = stack.getItem();
        this.lazytick$cachedHeldItem = player.getMainHandItem().getItem();

        this.lazytick$isBlacklisted = false;
        this.lazytick$cachedRecipe = result;
    }

    @Unique
    private void lazytick$clearCache() {
        this.lazytick$hasCached = false;
        this.lazytick$cachedTargetItem = null;
        this.lazytick$cachedHeldItem = null;
        this.lazytick$cachedRecipe = null;
        this.lazytick$isBlacklisted = false;
    }
}