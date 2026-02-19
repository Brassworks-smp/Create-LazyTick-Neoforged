package net.pinkcats.createlazytick.Register;

import com.simibubi.create.AllCreativeModeTabs;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.pinkcats.createlazytick.CreateLazyTick;
import net.pinkcats.createlazytick.config.ClientConfig;
import net.pinkcats.createlazytick.config.ServerConfig;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = CreateLazyTick.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientInit {

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == AllCreativeModeTabs.BASE_CREATIVE_TAB.get()) {
            event.accept(LazyTickItem.CLOCK.get());
        }
    }

    public static void initClient() {

        ModContainer container = ModLoadingContext.get().getActiveContainer();

        container.registerExtensionPoint(IConfigScreenFactory.class,
                (client, parent) -> new BaseConfigScreen(parent, CreateLazyTick.MODID)
                        .withSpecs(ClientConfig.SPEC, null, ServerConfig.SPEC)
        );
    }
}