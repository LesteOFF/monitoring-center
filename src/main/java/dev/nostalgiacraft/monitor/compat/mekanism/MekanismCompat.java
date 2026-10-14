package dev.nostalgiacraft.monitor.compat.mekanism;

import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import dev.nostalgiacraft.monitor.compat.OptionalEvents;
import dev.nostalgiacraft.monitor.metric.MetricReader;
import dev.nostalgiacraft.monitor.metric.Metrics;
import dev.nostalgiacraft.monitor.metric.Sample;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.radiation.IRadiationManager;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.server.level.ServerLevel;

public final class MekanismCompat {
    public static final String MOD_ID = "mekanism";
    private static final String MACHINES = "mekanism_machines";
    private static final String ENERGY = "energy";
    private static final int PERCENT = 100;

    private MekanismCompat() {
    }

    public static void register() {
        MetricReader universalEnergy = Metrics.get(ENERGY).reader();
        Metrics.direct(ENERGY, "FE", "bolt", (level, sensor) -> {
            Sample strict = OptionalEvents.guarded(MOD_ID, null, () -> strictEnergy(sensor));
            return strict != null ? strict : universalEnergy.read(level, sensor);
        });
        Metrics.direct("mek_progress", "%", "gear", (level, sensor) -> OptionalEvents.guarded(MACHINES, null, () -> progress(sensor)));
        Metrics.direct("mek_formed", "", "network", (level, sensor) -> OptionalEvents.guarded(MACHINES, null, () -> formed(sensor)));
        Metrics.direct("mek_chemical", "mB", "flask", (level, sensor) -> OptionalEvents.guarded(MOD_ID, null, () -> chemical(sensor)));
        Metrics.tanked("mek_chemical");
        Metrics.direct("mek_heat", "K", "thermometer", (level, sensor) -> OptionalEvents.guarded(MOD_ID, null, () -> heat(sensor)));
        Metrics.area("mek_radiation", "Sv/h", "radiation", (level, sensor) -> OptionalEvents.guarded(MOD_ID, null, () -> radiation(level, sensor)));
    }

    private static Sample strictEnergy(SensorBlockEntity sensor) {
        IStrictEnergyHandler handler = sensor.neighbor(Capabilities.STRICT_ENERGY.block());
        if (handler == null || handler.getEnergyContainerCount() == 0) {
            return null;
        }
        double stored = 0;
        double capacity = 0;
        for (int container = 0; container < handler.getEnergyContainerCount(); container++) {
            stored += EnergyUnit.FORGE_ENERGY.convertToDouble(handler.getEnergy(container));
            capacity += EnergyUnit.FORGE_ENERGY.convertToDouble(handler.getMaxEnergy(container));
        }
        return new Sample(stored, capacity);
    }

    private static Sample progress(SensorBlockEntity sensor) {
        return sensor.getLevel().getBlockEntity(sensor.targetPos()) instanceof TileEntityProgressMachine<?> machine
                ? new Sample(machine.getScaledProgress() * PERCENT, PERCENT) : null;
    }

    private static Sample formed(SensorBlockEntity sensor) {
        return sensor.getLevel().getBlockEntity(sensor.targetPos()) instanceof TileEntityMultiblock<?> part
                ? new Sample(part.getMultiblock().isFormed() ? 1 : 0, 1) : null;
    }

    private static Sample chemical(SensorBlockEntity sensor) {
        IChemicalHandler handler = sensor.neighbor(Capabilities.CHEMICAL.block());
        if (handler == null || handler.getChemicalTanks() == 0) {
            return null;
        }
        int tank = Math.min(sensor.tank(), handler.getChemicalTanks() - 1);
        return new Sample(handler.getChemicalInTank(tank).getAmount(), handler.getChemicalTankCapacity(tank));
    }

    private static Sample heat(SensorBlockEntity sensor) {
        IHeatHandler handler = sensor.neighbor(Capabilities.HEAT);
        return handler == null ? null : Sample.of(handler.getTotalTemperature());
    }

    private static Sample radiation(ServerLevel level, SensorBlockEntity sensor) {
        return Sample.of(IRadiationManager.INSTANCE.getRadiationLevel(level, sensor.getBlockPos()));
    }
}
