package net.pinkcats.createlazytick.Register;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.pinkcats.createlazytick.Channel.CLTChannel;

import static net.pinkcats.createlazytick.CreateLazyTick.MODID;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD)
public class AllChannel {

    @SubscribeEvent
    public static void registerChannel(RegisterPayloadHandlersEvent event) {
        CLTChannel.register(event);
    }
}