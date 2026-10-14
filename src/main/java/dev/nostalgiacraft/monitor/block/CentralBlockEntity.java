package dev.nostalgiacraft.monitor.block;

import dev.nostalgiacraft.monitor.config.ServerConfig;
import dev.nostalgiacraft.monitor.data.Channel;
import dev.nostalgiacraft.monitor.data.ChannelKey;
import dev.nostalgiacraft.monitor.data.MonitorNetwork;
import dev.nostalgiacraft.monitor.power.PowerState;
import dev.nostalgiacraft.monitor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public final class CentralBlockEntity extends BlockEntity implements ChannelMember {
    private static final String ENERGY = "energy";
    private static final int TICKS_PER_SECOND = 20;
    private static final int ROTATION_GRACE_TICKS = 2;

    private final PowerState power = new PowerState();
    private final IEnergyStorage energy = new Battery();
    private ChannelKey channel;
    private long lastRotationTick = Long.MIN_VALUE;
    private int savedEnergy;
    private boolean emitting;
    private int analogSignal;

    public CentralBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CENTRAL_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (channel != null && level instanceof ServerLevel server) {
            MonitorNetwork.get(server).attachCentral(channel, worldPosition);
        }
    }

    public void serverTick(ServerLevel server) {
        long tick = server.getGameTime();
        boolean rotating = lastRotationTick != Long.MIN_VALUE && tick - lastRotationTick <= ROTATION_GRACE_TICKS;
        Channel fed = MonitorNetwork.get(server).find(channel);
        boolean owner = fed != null && worldPosition.equals(fed.central());
        if (owner && power.tick(spec(), rotating)) {
            fed.feed(tick);
        }
        boolean alarming = owner && fed.powered(tick);
        signal(server, alarming && fed.alarms().emitting());
        analogSignal(server, alarming ? fed.alarms().signalStrength() : 0);
        if (tick % TICKS_PER_SECOND == 0 && power.stored() != savedEnergy) {
            savedEnergy = power.stored();
            setChanged();
        }
    }

    private void signal(ServerLevel server, boolean alarm) {
        if (alarm != emitting) {
            emitting = alarm;
            server.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private void analogSignal(ServerLevel server, int strength) {
        if (strength != analogSignal) {
            analogSignal = strength;
            server.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public boolean emitting() {
        return emitting;
    }

    public int analogSignal() {
        return analogSignal;
    }

    public void acceptRotation(long tick) {
        lastRotationTick = tick;
    }

    public IEnergyStorage energy() {
        return energy;
    }

    public boolean powered() {
        return power.powered();
    }

    @Override
    @Nullable
    public ChannelKey channel() {
        return channel;
    }

    @Override
    public boolean joinChannel(ChannelKey newChannel) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        MonitorNetwork network = MonitorNetwork.get(server);
        if (!network.attachCentral(newChannel, worldPosition)) {
            return false;
        }
        if (!newChannel.equals(channel)) {
            network.detachCentral(channel, worldPosition);
        }
        channel = newChannel;
        setChanged();
        return true;
    }

    private static PowerState.Spec spec() {
        int cost = ServerConfig.CENTRAL_FE_PER_TICK.get();
        return new PowerState.Spec(cost, Math.max(1, cost) * TICKS_PER_SECOND * ServerConfig.BATTERY_SECONDS.get());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (channel != null) {
            channel.save(tag);
        }
        tag.putInt(ENERGY, power.stored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = ChannelKey.load(tag);
        power.restore(tag.getInt(ENERGY));
    }

    private final class Battery implements IEnergyStorage {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return power.receive(spec(), amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return power.stored();
        }

        @Override
        public int getMaxEnergyStored() {
            return spec().capacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
