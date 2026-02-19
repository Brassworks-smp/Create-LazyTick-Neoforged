package net.pinkcats.createlazytick.manager;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.pinkcats.createlazytick.helper.util.RegistriesWrapper;
import net.pinkcats.createlazytick.Gui.mes;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public class LazyTickStatCache {
    private final String blockId;     
    private final UUID ownerUUID;       
    private final String ownerName;     
    private final long registeredTime;  
    private final int scrollValue;      
    private final boolean isForced;     

    public LazyTickStatCache(String blockId, UUID ownerUUID, String ownerName, long registeredTime,
                             int scrollValue, boolean isForced) {
        this.blockId = blockId;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.registeredTime = registeredTime;
        this.scrollValue = scrollValue;
        this.isForced = isForced;
    }

    public String getBlockId() { return blockId; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public int getScrollValue() { return scrollValue; }
    public boolean isForced() { return isForced; }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(registeredTime));
    }

    public long getRegisteredTime() {
        return registeredTime;
    }

    public Component getDisplayName() {
        try {

            ResourceLocation rl = ResourceLocation.tryParse(this.blockId);
            if (rl != null) {

                Block block = RegistriesWrapper.BLOCKS.getValue(rl);

                if (block != null && block != Blocks.AIR) {

                    return block.getName();
                }
            }
        } catch (Exception e) {
            mes.error("Error occurred when trying to parse the block id: "+ e.getMessage());
        }

        return mes.Char(this.blockId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LazyTickStatCache that = (LazyTickStatCache) o;
        return registeredTime == that.registeredTime &&
                scrollValue == that.scrollValue &&
                isForced == that.isForced &&
                Objects.equals(blockId, that.blockId) &&
                Objects.equals(ownerUUID, that.ownerUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId, ownerUUID, registeredTime, scrollValue, isForced);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", blockId);
        tag.putUUID("OwnerUUID", ownerUUID);
        tag.putString("Owner", ownerName);
        tag.putLong("Time", registeredTime);
        tag.putInt("Scroll", scrollValue);
        tag.putBoolean("IsForced", isForced);
        return tag;
    }

    public static LazyTickStatCache deserializeNBT(CompoundTag tag) {
        String name = tag.contains("Name") ? tag.getString("Name") : "Unknown";
        UUID ownerUUID = tag.contains("OwnerUUID") ? tag.getUUID("OwnerUUID") : Util.NIL_UUID;
        String owner = tag.contains("Owner") ? tag.getString("Owner") : "Unknown";
        long time = tag.contains("Time") ? tag.getLong("Time") : 0L;
        int scroll = tag.contains("Scroll") ? tag.getInt("Scroll") : 0;
        boolean forced = tag.contains("IsForced") && tag.getBoolean("IsForced");

        return new LazyTickStatCache(name, ownerUUID, owner, time, scroll, forced);
    }
}