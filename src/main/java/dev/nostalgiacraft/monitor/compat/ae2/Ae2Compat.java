package dev.nostalgiacraft.monitor.compat.ae2;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.compat.OptionalEvents;
import dev.nostalgiacraft.monitor.metric.MetricReader;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.metric.Sample;
import net.minecraft.core.Direction;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.BiFunction;

public final class Ae2Compat {
    public static final String MOD_ID = "ae2";
    private static final String ICON = "network";

    private Ae2Compat() {
    }

    public static void register() {
        Metrics.direct("ae2_items", "", "box", onGrid((grid, sensor) -> items(grid, sensor)));
        Metrics.filtered("ae2_items");
        Metrics.direct("ae2_fluid", "mB", "drop", onGrid((grid, sensor) -> fluid(grid, sensor)));
        Metrics.filtered("ae2_fluid");
        Metrics.direct("ae2_crafting", "", "gear", onGrid((grid, sensor) -> crafting(grid, sensor)));
        Metrics.filtered("ae2_crafting");
        Metrics.direct("ae2_types", "", "pile", onGrid((grid, sensor) -> Sample.of(grid.getStorageService().getCachedInventory().size())));
        Metrics.direct("ae2_energy", "AE", "battery", onGrid((grid, sensor) -> energy(grid.getEnergyService())));
        Metrics.direct("ae2_usage", "AE/t", "bolt", onGrid((grid, sensor) -> Sample.of(grid.getEnergyService().getAvgPowerUsage())));
        Metrics.direct("ae2_injection", "AE/t", "bolt", onGrid((grid, sensor) -> Sample.of(grid.getEnergyService().getAvgPowerInjection())));
        Metrics.direct("ae2_channels", "", ICON, onGrid((grid, sensor) -> Sample.of(grid.getPathingService().getUsedChannels())));
        Metrics.direct("ae2_cpus", "", "cpu", onGrid((grid, sensor) -> cpus(grid.getCraftingService().getCpus())));
    }

    private static MetricReader onGrid(BiFunction<IGrid, SensorBlockEntity, Sample> reading) {
        return (level, sensor) -> OptionalEvents.guarded(MOD_ID, null, () -> {
            IGrid grid = grid(level, sensor);
            return grid == null ? null : reading.apply(grid, sensor);
        });
    }

    @Nullable
    private static IGrid grid(ServerLevel level, SensorBlockEntity sensor) {
        IInWorldGridNodeHost host = GridHelper.getNodeHost(level, sensor.targetPos());
        if (host == null) {
            return null;
        }
        IGridNode facing = host.getGridNode(sensor.facing().getOpposite());
        if (facing != null) {
            return facing.getGrid();
        }
        for (Direction side : Direction.values()) {
            IGridNode node = host.getGridNode(side);
            if (node != null) {
                return node.getGrid();
            }
        }
        return null;
    }

    private static Sample items(IGrid grid, SensorBlockEntity sensor) {
        KeyCounter stored = grid.getStorageService().getCachedInventory();
        if (sensor.filterTag() != null) {
            return Sample.of(tagged(stored, sensor.filterTag()));
        }
        AEItemKey key = sensor.filter().isEmpty() ? null : AEItemKey.of(sensor.filter());
        return key == null ? null : Sample.of(stored.get(key));
    }

    private static long tagged(KeyCounter stored, TagKey<Item> tag) {
        long total = 0;
        for (Object2LongMap.Entry<AEKey> entry : stored) {
            if (entry.getKey() instanceof AEItemKey item && item.getItem().builtInRegistryHolder().is(tag)) {
                total += entry.getLongValue();
            }
        }
        return total;
    }

    private static Sample fluid(IGrid grid, SensorBlockEntity sensor) {
        FluidStack wanted = FluidUtil.getFluidContained(sensor.filter()).orElse(FluidStack.EMPTY);
        return wanted.isEmpty() ? null : Sample.of(grid.getStorageService().getCachedInventory().get(AEFluidKey.of(wanted)));
    }

    private static Sample crafting(IGrid grid, SensorBlockEntity sensor) {
        AEItemKey key = sensor.filter().isEmpty() ? null : AEItemKey.of(sensor.filter());
        return key == null ? null : Sample.of(grid.getCraftingService().getRequestedAmount(key));
    }

    private static Sample energy(IEnergyService energy) {
        return new Sample(energy.getStoredPower(), energy.getMaxStoredPower());
    }

    private static Sample cpus(Collection<ICraftingCPU> cpus) {
        int busy = 0;
        for (ICraftingCPU cpu : cpus) {
            if (cpu.isBusy()) {
                busy++;
            }
        }
        return new Sample(busy, cpus.size());
    }
}
