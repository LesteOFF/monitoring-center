package dev.nostalgiacraft.monitor.registry;

import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.block.CentralBlock;
import dev.nostalgiacraft.monitor.block.CentralBlockEntity;
import dev.nostalgiacraft.monitor.block.ScreenBlock;
import dev.nostalgiacraft.monitor.block.ScreenBlockEntity;
import dev.nostalgiacraft.monitor.block.SensorBlock;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MonitoringCenter.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
            MonitoringCenter.MODID);

    public static final DeferredBlock<SensorBlock> SENSOR = BLOCKS.registerBlock("sensor", SensorBlock::new, machine().noOcclusion());
    public static final DeferredItem<BlockItem> SENSOR_ITEM = ModItems.ITEMS.registerSimpleBlockItem(SENSOR);
    public static final DeferredBlock<CentralBlock> CENTRAL = BLOCKS.registerBlock("central", CentralBlock::new, machine());
    public static final DeferredItem<BlockItem> CENTRAL_ITEM = ModItems.ITEMS.registerSimpleBlockItem(CENTRAL);
    public static final DeferredBlock<ScreenBlock> SCREEN = BLOCKS.registerBlock("screen", ScreenBlock::new, machine().noOcclusion());
    public static final DeferredItem<BlockItem> SCREEN_ITEM = ModItems.ITEMS.registerSimpleBlockItem(SCREEN);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SensorBlockEntity>> SENSOR_ENTITY = BLOCK_ENTITIES.register(
            "sensor", () -> BlockEntityType.Builder.of(SensorBlockEntity::new, SENSOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentralBlockEntity>> CENTRAL_ENTITY = BLOCK_ENTITIES.register(
            "central", () -> BlockEntityType.Builder.of(CentralBlockEntity::new, CENTRAL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScreenBlockEntity>> SCREEN_ENTITY = BLOCK_ENTITIES.register(
            "screen", () -> BlockEntityType.Builder.of(ScreenBlockEntity::new, SCREEN.get()).build(null));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ModBlocks::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CENTRAL_ENTITY.get(), (central, side) -> central.energy());
    }

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6f).sound(SoundType.METAL);
    }
}
