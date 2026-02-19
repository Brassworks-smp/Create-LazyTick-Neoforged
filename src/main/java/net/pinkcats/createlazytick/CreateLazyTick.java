package net.pinkcats.createlazytick;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.pinkcats.createlazytick.Register.LazyTickItem;
import net.pinkcats.createlazytick.config.ServerConfig;
import net.pinkcats.createlazytick.config.ClientConfig;
import org.slf4j.Logger;

import static net.pinkcats.createlazytick.Register.LazyTickCommand.RegisterCLTCommand;

@Mod(CreateLazyTick.MODID)
public class CreateLazyTick {
    public static final String MODID = "createlazytick";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean IsServerReload = false;

    public static ResourceLocation DropResourceLocation(String Location){
        return ResourceLocation.parse(Location);
    }
    public static ResourceLocation DropResourceLocation(String NameSpace, String Path){
        return ResourceLocation.fromNamespaceAndPath(NameSpace, Path);
    }

    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    public CreateLazyTick(IEventBus modEventBus, ModContainer modContainer) {

        LazyTickItem.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        if (FMLEnvironment.dist.isClient()) {
            net.pinkcats.createlazytick.Register.ClientInit.initClient();
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        RegisterCLTCommand(event);
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }

    }
}