package dev.nostalgiacraft.monitor.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Loaded only after the client verifies that Create is installed. */
public final class CreateCompatClient {
    private CreateCompatClient() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateCompat.couplerEntity(), CouplerRenderer::new);
    }

    private static final class CouplerRenderer extends KineticBlockEntityRenderer<KineticCouplerBlockEntity> {
        CouplerRenderer(BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void renderSafe(KineticCouplerBlockEntity coupler, float partialTicks, PoseStack poseStack,
                                  MultiBufferSource buffers, int light, int overlay) {
            Direction input = coupler.getBlockState().getValue(KineticCouplerBlock.FACING).getOpposite();
            // No Flywheel visualizer is registered for this optional block. Always draw its input
            // here, including when Flywheel renders Create's own machines using instancing.
            renderRotatingBuffer(coupler,
                    CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, coupler.getBlockState(), input),
                    poseStack, buffers.getBuffer(RenderType.solid()), light);
        }
    }
}
