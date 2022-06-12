package de.mlessmann.dw20patches;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("dw20patches")
public class DW20PatchesMod {
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public DW20PatchesMod() {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the enqueueIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // some preinit code
        LOGGER.info("DW20Patches installed.");
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {
        // Some example code to dispatch IMC to another mod
        // InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the MDK"); return "Hello world";});
    }

    private void processIMC(final InterModProcessEvent event) {
        // Some example code to receive and process InterModComms from other mods
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m -> m.messageSupplier().get()).toList());
    }


    /**
     * In some chunks, flowing lava fluid can crash the server due to a StackOverflowError that occurs.
     * What happens is <em>something changes -> onNeighbourChange -> shouldSpread -> blockPlace -> onNeighbourChange -> shouldSpread (...)<em/>.
     * We want to prevent this by trying to predict when things have gone to far and stopping the recursion.
     *
     * @implNote Because we cannot simply throw an exception (undefined behavior might be the result), we rather try to get a little early by cancelling the NeighbourNotify event.
     * @see LiquidBlock#onNeighborChange(BlockState, LevelReader, BlockPos, BlockPos)
     */
    @SuppressWarnings("java:S1066")
    @SubscribeEvent
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().getBlock() instanceof LiquidBlock liquid && liquid.getFluid().is(FluidTags.LAVA)) {
            // The cancellable method "updateNeighborsAt" was called ~99 times before the error occurred
            // ... and the recursion stack trace was 9 elements long.
            // As a "safe" bet that both cancels a little early but leaves enough time for "larger flows", we chose 90 * 9 as the max trace length.
            if (Thread.currentThread().getStackTrace().length > 810) {
                LOGGER.warn("Lava-StackOverflow imminent -> cancelling #onNeighbourNotify! This was at {}", event.getPos());
                event.setCanceled(true);
            }
        }
    }

    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
    }
}
