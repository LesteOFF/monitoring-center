package dev.nostalgiacraft.monitor.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.nostalgiacraft.monitor.MonitoringCenter;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.compat.OptionalEvents;
import dev.nostalgiacraft.monitor.config.ServerConfig;
import dev.nostalgiacraft.monitor.data.SensorScheduler;
import dev.nostalgiacraft.monitor.metric.MetricReader;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.metric.Sample;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import dev.nostalgiacraft.monitor.registry.ModItems;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

public final class CreateCompat {
    public static final String MOD_ID = "create";
    private static final String COUPLER_NAME = "kinetic_coupler";
    private static final double MAX_RPM = 256;
    private static final double DEFAULT_IMPACT = 4;
    private static final DeferredRegister<DisplayTarget> TARGETS = DeferredRegister.create(CreateRegistries.DISPLAY_TARGET, MonitoringCenter.MODID);
    private static final DeferredRegister<DisplaySource> SOURCES = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, MonitoringCenter.MODID);
    private static final DeferredHolder<DisplayTarget, ScreenDisplayTarget> SCREEN_TARGET = TARGETS.register("screen", ScreenDisplayTarget::new);
    private static final DeferredHolder<DisplaySource, SensorDisplaySource> SENSOR_SOURCE = SOURCES.register("sensor", SensorDisplaySource::new);
    private static final Map<KineticNetwork, CachedStress> STRESS = new WeakHashMap<>();
    private static DeferredBlock<KineticCouplerBlock> coupler;
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<KineticCouplerBlockEntity>> couplerEntity;

    private record CachedStress(long tick, Sample sample) {
    }

    private CreateCompat() {
    }

    public static void register() {
        IEventBus modBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        TARGETS.register(modBus);
        SOURCES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(CreateCompat::link));
        coupler = ModBlocks.BLOCKS.registerBlock(COUPLER_NAME, KineticCouplerBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6f).sound(SoundType.METAL).noOcclusion());
        ModItems.ITEMS.registerSimpleBlockItem(coupler);
        couplerEntity = ModBlocks.BLOCK_ENTITIES.register(COUPLER_NAME,
                () -> BlockEntityType.Builder.of(KineticCouplerBlockEntity::new, coupler.get()).build(null));
        Metrics.direct("create_speed", "RPM", "gear", kinetic(wheel -> new Sample(Math.abs(wheel.getSpeed()), MAX_RPM)));
        Metrics.direct("create_stress", "SU", "gauge", kinetic(CreateCompat::stress));
        Metrics.direct("create_overstressed", "", "warning", kinetic(wheel -> new Sample(wheel.isOverStressed() ? 1 : 0, 1)));
    }

    static BlockEntityType<KineticCouplerBlockEntity> couplerEntity() {
        return couplerEntity.get();
    }

    private static void link() {
        BlockStressValues.IMPACTS.register(coupler.get(), CreateCompat::impact);
        DisplayTarget.BY_BLOCK.register(ModBlocks.SCREEN.get(), SCREEN_TARGET.get());
        DisplaySource.BY_BLOCK.add(ModBlocks.SENSOR.get(), SENSOR_SOURCE.get());
    }

    private static double impact() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.COUPLER_STRESS_IMPACT.get() : DEFAULT_IMPACT;
    }

    private static MetricReader kinetic(Function<KineticBlockEntity, Sample> reading) {
        return (level, sensor) -> OptionalEvents.guarded(MOD_ID, null, () -> read(sensor, reading));
    }

    private static Sample read(SensorBlockEntity sensor, Function<KineticBlockEntity, Sample> reading) {
        return sensor.getLevel().getBlockEntity(sensor.targetPos()) instanceof KineticBlockEntity wheel ? reading.apply(wheel) : null;
    }

    private static Sample stress(KineticBlockEntity wheel) {
        if (!wheel.hasNetwork()) {
            return new Sample(0, 0);
        }
        KineticNetwork network = wheel.getOrCreateNetwork();
        long tick = wheel.getLevel().getGameTime();
        CachedStress cached = STRESS.get(network);
        if (cached == null || tick < cached.tick() || tick - cached.tick() >= SensorScheduler.ACTIVE_PERIOD) {
            cached = new CachedStress(tick, new Sample(network.calculateStress(), network.calculateCapacity()));
            STRESS.put(network, cached);
        }
        return cached.sample();
    }
}
