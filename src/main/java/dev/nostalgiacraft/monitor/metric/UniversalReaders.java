package dev.nostalgiacraft.monitor.metric;

import dev.nostalgiacraft.monitor.block.SensorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Map;

final class UniversalReaders {
    private static final int MAX_SIGNAL = 15;

    private UniversalReaders() {
    }

    static Sample energy(ServerLevel level, SensorBlockEntity sensor) {
        IEnergyStorage storage = sensor.neighbor(Capabilities.EnergyStorage.BLOCK);
        return storage == null ? null : new Sample(storage.getEnergyStored(), storage.getMaxEnergyStored());
    }

    static Sample fluid(ServerLevel level, SensorBlockEntity sensor) {
        IFluidHandler handler = sensor.neighbor(Capabilities.FluidHandler.BLOCK);
        if (handler == null || handler.getTanks() == 0) {
            return null;
        }
        int tank = Math.min(sensor.tank(), handler.getTanks() - 1);
        FluidStack stored = handler.getFluidInTank(tank);
        sensor.observeIcon("fluid", stored.isEmpty() ? "" : "fluid:" + BuiltInRegistries.FLUID.getKey(stored.getFluid()));
        return new Sample(stored.getAmount(), handler.getTankCapacity(tank));
    }

    static Sample fluidTotal(ServerLevel level, SensorBlockEntity sensor) {
        IFluidHandler handler = sensor.neighbor(Capabilities.FluidHandler.BLOCK);
        if (handler == null || handler.getTanks() == 0) {
            return null;
        }
        FluidStack wanted = FluidUtil.getFluidContained(sensor.filter()).orElse(FluidStack.EMPTY);
        long stored = 0;
        long capacity = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack held = handler.getFluidInTank(tank);
            boolean counted = !held.isEmpty() && (wanted.isEmpty() || FluidStack.isSameFluid(held, wanted));
            stored += counted ? held.getAmount() : 0;
            capacity += counted || held.isEmpty() ? handler.getTankCapacity(tank) : 0;
        }
        return new Sample(stored, capacity);
    }

    static Sample items(ServerLevel level, SensorBlockEntity sensor) {
        IItemHandler handler = sensor.neighbor(Capabilities.ItemHandler.BLOCK);
        if (handler == null || handler.getSlots() == 0) {
            return null;
        }
        ItemStack filter = sensor.filter();
        long stored = 0;
        long room = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            boolean counted = !stack.isEmpty() && sensor.accepts(stack);
            if (counted) {
                stored += stack.getCount();
            }
            if (counted || stack.isEmpty()) {
                room += Math.max(stack.getCount(), room(handler.getSlotLimit(slot), stack.isEmpty() ? filter : stack));
            }
        }
        return new Sample(stored, room);
    }

    private static long room(int slotLimit, ItemStack kind) {
        if (slotLimit > Item.ABSOLUTE_MAX_STACK_SIZE) {
            return slotLimit;
        }
        return Math.min(slotLimit, kind.isEmpty() ? Item.DEFAULT_MAX_STACK_SIZE : kind.getMaxStackSize());
    }

    static Sample itemIn(ServerLevel level, SensorBlockEntity sensor) {
        return hasItems(sensor) ? Sample.of(sensor.flow().takeRates(level.getGameTime()).in()) : null;
    }

    static Sample itemOut(ServerLevel level, SensorBlockEntity sensor) {
        return hasItems(sensor) ? Sample.of(sensor.flow().takeRates(level.getGameTime()).out()) : null;
    }

    static void trackItemFlow(SensorBlockEntity sensor) {
        IItemHandler handler = sensor.neighbor(Capabilities.ItemHandler.BLOCK);
        if (handler == null) {
            return;
        }
        Map<Item, Long> counts = new HashMap<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && sensor.accepts(stack)) {
                counts.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
            }
        }
        sensor.flow().observe(counts, sensor.getLevel().getGameTime());
    }

    private static boolean hasItems(SensorBlockEntity sensor) {
        IItemHandler handler = sensor.neighbor(Capabilities.ItemHandler.BLOCK);
        return handler != null && handler.getSlots() > 0;
    }

    static Sample redstone(ServerLevel level, SensorBlockEntity sensor) {
        BlockPos target = sensor.targetPos();
        if (!level.isLoaded(target)) {
            return null;
        }
        BlockState state = level.getBlockState(target);
        int signal = state.hasAnalogOutputSignal() ? state.getAnalogOutputSignal(level, target) : level.getSignal(target, sensor.facing());
        return new Sample(signal, MAX_SIGNAL);
    }
}
