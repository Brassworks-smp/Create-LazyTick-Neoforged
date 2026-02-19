package net.pinkcats.createlazytick.mixin.OptElement.saw;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.recipe.RecipeConditions;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.pinkcats.createlazytick.config.ServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.pinkcats.createlazytick.CreateLazyTick.IsServerReload;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@Mixin(value = SawBlockEntity.class, remap = false)
public class SawRecipeMixin extends BlockBreakingKineticBlockEntity {

    @Shadow(remap = false)
    private static final Object cuttingRecipesKey = new Object();

    @Shadow(remap = false)
    private FilteringBehaviour filtering;

    @Shadow(remap = false)
    public ProcessingInventory inventory;

    @Unique private ItemStack lazytick$lastFilterStackSnapshot = ItemStack.EMPTY;

    @Unique private ItemStack lazytick$lastFilterInstance = null;

    @Unique private Item lazytick$lastInputItem = null;

    @Unique private List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> lazytick$lastFilteredResult = null;

    public SawRecipeMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "getRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private void getRecipes(CallbackInfoReturnable<List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>>> cir) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableCacheSaw()) {
            return;
        }
        if (IsServerReload)
            createLazyTick$ClearCache();

        ItemStack HandleItem = inventory.getStackInSlot(0);
        if (HandleItem.isEmpty()) return;

        ItemStack currentFilter = filtering.getFilter();
        Item currentInputItem = HandleItem.getItem();

        if (lazytick$lastFilteredResult != null &&
                currentInputItem == lazytick$lastInputItem &&
                currentFilter == lazytick$lastFilterInstance) {

            cir.setReturnValue(lazytick$lastFilteredResult);
            cir.cancel();
            return;
        }

        if (lazytick$lastFilteredResult != null &&
                currentInputItem == lazytick$lastInputItem &&
                ItemStack.isSameItemSameComponents(currentFilter, lazytick$lastFilterStackSnapshot)) {

            this.lazytick$lastFilterInstance = currentFilter;

            cir.setReturnValue(lazytick$lastFilteredResult);
            cir.cancel();
            return;
        }

        List<RecipeHolder<CuttingRecipe>> assemblyRecipes = createLazyTick$GetAssemblyRecipeCache(HandleItem);

        if (!assemblyRecipes.isEmpty()) {
            RecipeHolder<CuttingRecipe> recipeHolder = assemblyRecipes.get(0);

            if (level != null && filtering.test(recipeHolder.value().getResultItem(level.registryAccess()))) {
                List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> castedAssemblyList = new ArrayList<>(assemblyRecipes);
                createLazyTick$UpdateSnapshot(currentInputItem, currentFilter, castedAssemblyList);
                cir.setReturnValue(castedAssemblyList);
                cir.cancel();
                return;
            }
        }

        List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> cachedAllRecipes = createLazyTick$GetRecipeCache(HandleItem);

        List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> filteredRecipes = cachedAllRecipes.stream()
                .filter(RecipeConditions.outputMatchesFilter(filtering))
                .collect(Collectors.toList());

        createLazyTick$UpdateSnapshot(currentInputItem, currentFilter, filteredRecipes);

        cir.setReturnValue(filteredRecipes);
        cir.cancel();
    }

    @Unique
    private void createLazyTick$UpdateSnapshot(Item input, ItemStack filter, List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> result) {
        this.lazytick$lastInputItem = input;

        this.lazytick$lastFilterStackSnapshot = filter.copy(); 
        this.lazytick$lastFilterInstance = filter;             

        this.lazytick$lastFilteredResult = result;
    }

    @Unique
    private Map<Item, ImmutableList<RecipeHolder<CuttingRecipe>>> createLazyTick$assemblyRecipeCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Item, ImmutableList<RecipeHolder<CuttingRecipe>>> eldest) {
            return size() > ServerConfig.getSawCacheMax(); 
        }
    };

    @Unique
    private ImmutableList<RecipeHolder<CuttingRecipe>> createLazyTick$GetAssemblyRecipeCache(ItemStack itemStack) {

        if (createLazyTick$assemblyRecipeCache.containsKey(itemStack.getItem())) {
            return createLazyTick$assemblyRecipeCache.get(itemStack.getItem());
        }

        boolean hasTag = !itemStack.getComponentsPatch().isEmpty();

        Optional<RecipeHolder<CuttingRecipe>> assemblyRecipe = Optional.empty();
        if (level != null) {
            assemblyRecipe = SequencedAssemblyRecipe.getRecipe(level, itemStack,
                    AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class);
        }

        if (assemblyRecipe.isPresent()) {
            if (!hasTag) {
                createLazyTick$assemblyRecipeCache.put(itemStack.getItem(), ImmutableList.of(assemblyRecipe.get()));
            }
            return ImmutableList.of(assemblyRecipe.get());
        } else {
            if (!hasTag) {
                createLazyTick$assemblyRecipeCache.put(itemStack.getItem(), ImmutableList.of());
                return ImmutableList.of();
            } else {
                if (level == null) return ImmutableList.of();
                Optional<RecipeHolder<CuttingRecipe>> cleanCheck = SequencedAssemblyRecipe.getRecipe(level, new ItemStack(itemStack.getItem()),
                        AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class);

                if (cleanCheck.isEmpty()) {
                    createLazyTick$assemblyRecipeCache.put(itemStack.getItem(), ImmutableList.of());
                }
                return ImmutableList.of();
            }
        }
    }

    @Override
    protected BlockPos getBreakingPos() {
        return getBlockPos().relative(getBlockState().getValue(SawBlock.FACING));
    }

    @Unique
    private Map<Item, List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>>> createLazyTick$recipeCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Item, List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>>> eldest) {
            return size() > ServerConfig.getSawCacheMax(); 
        }
    };

    @Unique
    private List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> createLazyTick$GetRecipeCache(ItemStack itemStack) {

        if (createLazyTick$recipeCache.containsKey(itemStack.getItem())) {
            return createLazyTick$recipeCache.get(itemStack.getItem());
        }

        boolean hasTag = !itemStack.getComponentsPatch().isEmpty();

        Predicate<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> types = RecipeConditions.isOfType(AllRecipeTypes.CUTTING.getType(),
                AllConfigs.server().recipes.allowStonecuttingOnSaw.get() ? RecipeType.STONECUTTING : null);

        @SuppressWarnings("unchecked")
        List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> startedSearch =
                (List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>>)(Object) RecipeFinder.get(cuttingRecipesKey, level, types);

        List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> recipes = startedSearch.stream()
                .filter(RecipeConditions.firstIngredientMatches(inventory.getStackInSlot(0)))
                .filter(r -> !AllRecipeTypes.shouldIgnoreInAutomation(r))
                .collect(Collectors.toList());

        if (!recipes.isEmpty()) {
            if (!hasTag) {
                createLazyTick$recipeCache.put(itemStack.getItem(), recipes);
            }
            return recipes;
        } else {
            if (!hasTag) {
                createLazyTick$recipeCache.put(itemStack.getItem(), Collections.emptyList());
                return Collections.emptyList();
            } else {
                ItemStack cleanStack = new ItemStack(itemStack.getItem());

                boolean cleanHasRecipe = startedSearch.stream()
                        .filter(RecipeConditions.firstIngredientMatches(cleanStack))
                        .anyMatch(r -> !AllRecipeTypes.shouldIgnoreInAutomation(r));
                if (!cleanHasRecipe) {
                    createLazyTick$recipeCache.put(itemStack.getItem(), Collections.emptyList());
                }

                return Collections.emptyList();
            }
        }
    }

    @Unique
    private void createLazyTick$ClearCache() {
        createLazyTick$recipeCache.clear();
        createLazyTick$assemblyRecipeCache.clear();
        lazytick$lastInputItem = null;
        lazytick$lastFilterStackSnapshot = ItemStack.EMPTY;
        lazytick$lastFilterInstance = null;
        lazytick$lastFilteredResult = null;
    }
}