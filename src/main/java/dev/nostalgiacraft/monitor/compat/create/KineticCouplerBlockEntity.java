package dev.nostalgiacraft.monitor.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.nostalgiacraft.monitor.block.CentralBlockEntity;
import dev.nostalgiacraft.monitor.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class KineticCouplerBlockEntity extends KineticBlockEntity {
    public KineticCouplerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCompat.couplerEntity(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerLevel server && spinsFastEnough()
                && server.getBlockEntity(worldPosition.relative(getBlockState().getValue(KineticCouplerBlock.FACING))) instanceof CentralBlockEntity central) {
            central.acceptRotation(server.getGameTime());
        }
    }

    private boolean spinsFastEnough() {
        return !isOverStressed() && Math.abs(getSpeed()) >= ServerConfig.COUPLER_MIN_RPM.get();
    }
}
