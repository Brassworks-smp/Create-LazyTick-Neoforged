package net.pinkcats.createlazytick.helper.util;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class RegistriesWrapper {
    public static final RegistryWrapper<Block> BLOCKS = new RegistryWrapper<>(BuiltInRegistries.BLOCK);
    public static final RegistryWrapper<Item> ITEMS = new RegistryWrapper<>(BuiltInRegistries.ITEM);
    public static final RegistryWrapper<EntityType<?>> ENTITY_TYPES = new RegistryWrapper<>(BuiltInRegistries.ENTITY_TYPE);
    public static final RegistryWrapper<SoundEvent> SOUND_EVENTS = new RegistryWrapper<>(BuiltInRegistries.SOUND_EVENT);
    public static final RegistryWrapper<Fluid> FLUIDS = new RegistryWrapper<>(BuiltInRegistries.FLUID);
    public static final RegistryWrapper<MobEffect> MOB_EFFECTS = new RegistryWrapper<>(BuiltInRegistries.MOB_EFFECT);
    public static final RegistryWrapper<RecipeSerializer<?>> RECIPE_SERIALIZERS = new RegistryWrapper<>(BuiltInRegistries.RECIPE_SERIALIZER);

    public static class RegistryWrapper<T> {
        private final Registry<T> registry;
        public RegistryWrapper(Registry<T> registry) { this.registry = registry; }
        public T getValue(ResourceLocation key) { return registry.get(key); }
        public ResourceLocation getKey(T value) { return registry.getKey(value); }
        public boolean containsKey(ResourceLocation key) { return registry.containsKey(key); }
        public boolean containsValue(T value) { return registry.getKey(value) != null; }
        public Collection<T> getValues() { return registry.stream().collect(Collectors.toList()); }
        public Set<ResourceLocation> getKeys() { return registry.keySet(); }
        public Registry<T> getRegistry() { return registry; }
    }
}