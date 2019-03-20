package net.silentchaos512.gear;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DeferredWorkQueue;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.event.lifecycle.*;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.silentchaos512.gear.client.DebugOverlay;
import net.silentchaos512.gear.client.event.ExtraBlockBreakHandler;
import net.silentchaos512.gear.client.event.TooltipHandler;
import net.silentchaos512.gear.client.gui.GuiTypes;
import net.silentchaos512.gear.client.models.ArmorItemModel;
import net.silentchaos512.gear.client.models.ToolModel;
import net.silentchaos512.gear.command.LockStatsCommand;
import net.silentchaos512.gear.command.SGearPartsCommand;
import net.silentchaos512.gear.config.Config;
import net.silentchaos512.gear.init.*;
import net.silentchaos512.gear.network.Network;
import net.silentchaos512.gear.parts.PartManager;
import net.silentchaos512.gear.traits.TraitManager;
import net.silentchaos512.gear.util.GenModels;
import net.silentchaos512.gear.util.GenRecipes;
import net.silentchaos512.gear.util.IAOETool;
import net.silentchaos512.gear.world.ModWorldFeatures;
import net.silentchaos512.lib.event.InitialSpawnItems;
import net.silentchaos512.lib.inventory.ContainerType;

import java.util.function.BiFunction;

class SideProxy {
    SideProxy() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::imcEnqueue);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::imcProcess);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(ModBlocks::registerAll);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ModItems::registerAll);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ModTileEntities::registerAll);

        MinecraftForge.EVENT_BUS.addListener(this::serverAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);

        registerContainersCommon();

        Config.init();
        Network.init();

        ModLootStuff.init();
        ModRecipes.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        DeferredWorkQueue.runLater(ModWorldFeatures::addFeaturesToBiomes);

        IAOETool.BreakHandler.buildOreBlocksSet();

        InitialSpawnItems.add(new ResourceLocation(SilentGear.MOD_ID, "starter_blueprints"), () -> {
            if (Config.GENERAL.spawnWithStarterBlueprints.get())
                return ModItems.blueprintPackage.getStack();
            else return ItemStack.EMPTY;
        });

        if (SilentGear.isDevBuild() && SilentGear.RUN_GENERATORS) {
            ModTags.init();
            GenModels.generateAll();
            GenRecipes.generateAll();
        }
    }

    private void imcEnqueue(InterModEnqueueEvent event) { }

    private void imcProcess(InterModProcessEvent event) { }

    private void serverAboutToStart(FMLServerAboutToStartEvent event) {
        IReloadableResourceManager resourceManager = event.getServer().getResourceManager();
        resourceManager.addReloadListener(TraitManager.INSTANCE);
        resourceManager.addReloadListener(PartManager.INSTANCE);

        CommandDispatcher<CommandSource> dispatcher = event.getServer().getCommandManager().getDispatcher();
        LockStatsCommand.register(dispatcher);
        SGearPartsCommand.register(dispatcher);
    }

    private void serverStarted(FMLServerStartedEvent event) {
        SilentGear.LOGGER.info(PartManager.MARKER, "Total gear parts loaded: {}", PartManager.getValues().size());
    }

    private static void registerContainersCommon() {
        for (GuiTypes type : GuiTypes.values()) {
            //noinspection Convert2MethodRef -- compiler error
            ContainerType.register(type::getContainerType, (tileType, player) ->
                    type.getContainer(tileType, player));
        }
    }

    static class Client extends SideProxy {
        Client() {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

            MinecraftForge.EVENT_BUS.register(ExtraBlockBreakHandler.INSTANCE);
            MinecraftForge.EVENT_BUS.register(TooltipHandler.INSTANCE);
            MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);

            if (SilentGear.isDevBuild()) {
                MinecraftForge.EVENT_BUS.register(new DebugOverlay());
            }

            registerContainers();

            // FIXME: These do not work!
            ModelLoaderRegistry.registerLoader(ToolModel.Loader.INSTANCE);
            ModelLoaderRegistry.registerLoader(ArmorItemModel.Loader.INSTANCE);
        }

        private void clientSetup(FMLClientSetupEvent event) { }

        private static void registerContainers() {
            for (GuiTypes type : GuiTypes.values()) {
                //noinspection Convert2MethodRef -- compiler error
                ContainerType.registerGui(type::getContainerType, (tileType, player) ->
                        type.getGui(tileType, player));
            }

            ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.GUIFACTORY, () -> packet -> {
                ContainerType<?> type = ContainerType.factories.get(packet.getId()).get();
                if (packet.getAdditionalData() != null) type.fromBytes(packet.getAdditionalData());
                //noinspection unchecked
                return ((BiFunction<ContainerType<?>, EntityPlayer, GuiContainer>) ContainerType.guiFactories.get(packet.getId()))
                        .apply(type, Minecraft.getInstance().player);
            });
        }

        private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            /*
            if (Loader.isModLoaded("jei")) {
                if (JeiPlugin.hasInitFailed()) {
                    String msg = "The JEI plugin seems to have failed. Some recipes may not be visible. Please report with a copy of your log file.";
                    SilentGear.log.error(msg);
                    event.player.sendMessage(new TextComponentString(TextFormatting.RED + "[Silent Gear] " + msg));
                } else {
                    SilentGear.log.info("JEI plugin seems to have loaded correctly.");
                }
            } else {
                SilentGear.log.info("JEI is not installed?");
            }
            */
        }
    }

    static class Server extends SideProxy {
        Server() {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverSetup);
        }

        private void serverSetup(FMLDedicatedServerSetupEvent event) { }
    }
}