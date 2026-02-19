package net.pinkcats.createlazytick.bridge.Crafter;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.GroupedItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item; 
import net.minecraft.world.item.ItemStack;
import net.pinkcats.createlazytick.mixin.OptElement.crafter.CrafterAccessor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class CrafterGridSignature {
    private final int calculatedHashCode; 
    public final boolean isCacheable; 
    public final String nbtCulpritName; 

    private final List<SimpleItemInfo> sortedGrid; 

    public CrafterGridSignature(GroupedItems items) {
        CrafterAccessor accessor = (CrafterAccessor) items;

        Map<Pair<Integer, Integer>, ItemStack> grid = accessor.getGrid();
        int minX = accessor.getMinX();
        int minY = accessor.getMinY();

        int h = 0;

        boolean safe = true;
        String culprit = null;

        List<SimpleItemInfo> tempGrid = new ArrayList<>(grid.size());

        for (Map.Entry<Pair<Integer, Integer>, ItemStack> entry : grid.entrySet()) {
            ItemStack stack = entry.getValue();
            if (stack.isEmpty()) continue;

            if (!stack.getComponentsPatch().isEmpty()) {
                safe = false;

                culprit = stack.getItem().getDescriptionId();
                break;
            }

            int relX = entry.getKey().getKey() - minX;
            int relY = entry.getKey().getValue() - minY;

            h += Objects.hash(relX, relY, stack.getItem(), stack.getCount());

            tempGrid.add(new SimpleItemInfo(relX, relY, stack));
        }

        if (safe) {
            tempGrid.sort(Comparator.comparingInt((SimpleItemInfo i) -> i.y).thenComparingInt(i -> i.x));
        }

        this.isCacheable = safe;
        this.nbtCulpritName = culprit;
        this.calculatedHashCode = h;
        this.sortedGrid = safe ? tempGrid : null;
    }

    @Override
    public int hashCode() {
        return calculatedHashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CrafterGridSignature other = (CrafterGridSignature) obj;

        if (this.calculatedHashCode != other.calculatedHashCode) return false;
        if (this.sortedGrid == null || other.sortedGrid == null) return false;
        if (this.sortedGrid.size() != other.sortedGrid.size()) return false;

        for (int i = 0; i < this.sortedGrid.size(); i++) {
            if (!this.sortedGrid.get(i).isSame(other.sortedGrid.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static class SimpleItemInfo {
        final int x, y;
        final Item item;       
        final int count;       

        public SimpleItemInfo(int x, int y, ItemStack stack) {
            this.x = x;
            this.y = y;

            this.item = stack.getItem();
            this.count = stack.getCount();
        }

        public boolean isSame(SimpleItemInfo other) {
            return this.x == other.x &&
                    this.y == other.y &&
                    this.item == other.item && 
                    this.count == other.count; 
        }
    }
}