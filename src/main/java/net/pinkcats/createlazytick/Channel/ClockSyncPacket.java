package net.pinkcats.createlazytick.Channel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import static net.pinkcats.createlazytick.CreateLazyTick.DropResourceLocation;
import static net.pinkcats.createlazytick.CreateLazyTick.MODID;
import net.pinkcats.createlazytick.Gui.mes;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ClockSyncPacket implements CustomPacketPayload {

    private final BlockPos pos;
    private final String dimension;
    private final int extraData;
    private final boolean isQuery;

    public static List<ClientData> PacketCache = new ArrayList<>();

    public ClockSyncPacket(BlockPos pos) {
        this.pos = pos;
        this.dimension = "";
        this.extraData = 0;
        this.isQuery = true; 
    }

    public ClockSyncPacket(int extraData , String dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
        this.extraData = extraData;
        this.isQuery = false; 
    }

    public ClockSyncPacket(FriendlyByteBuf buf) {
        dimension = buf.readUtf();
        pos = buf.readBlockPos();
        extraData = buf.readInt();
        isQuery = buf.readBoolean(); 
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension);
        buf.writeBlockPos(pos);
        buf.writeInt(extraData);
        buf.writeBoolean(isQuery);
    }
    public static final Type<ClockSyncPacket> TYPE = new Type<>(DropResourceLocation(MODID, "dimension_to_server"));

    public static final StreamCodec<FriendlyByteBuf, ClockSyncPacket> STREAM_CODEC = StreamCodec.ofMember(
            ClockSyncPacket::encode, ClockSyncPacket::new
    );

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (isQuery) {
                Level level = player.level();
                if (level.isLoaded(pos)) {
                    if (level.getBlockEntity(pos) instanceof ISmartBlockEntityControl control) {
                        control.createLazyTick$sendBlockUpdated();
                    }
                }
                return;
            }

            ClientData data = new ClientData(extraData, dimension, pos);

            if (PacketCache.size() > 80) {
                mes.error("ServerPacket Cargo is full. This shouldn't happen!");
                PacketCache.clear();
            }

            for (ClientData existingData : PacketCache) {
                if (data.isSimilar(existingData))
                    return;
            }
            PacketCache.add(data);
        });
    }

    @Override
    public String toString() {
        return "Packet{" +
                "dimension=" + dimension +
                ", pos="  + pos +
                ", extraData="  + extraData +
                '}';
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}