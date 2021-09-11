package mtr.render;

import mtr.block.IBlock;
import mtr.block.IPropagateBlock;
import mtr.data.IGui;
import mtr.data.Platform;
import mtr.gui.ClientData;
import mtr.gui.RenderingInstruction;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.ArrayList;
import java.util.List;

public abstract class RenderRouteBase<T extends BlockEntity> extends BlockEntityRenderer<T> implements IGui {

	private static final float EXTRA_PADDING = 0.0625F;

	public RenderRouteBase(BlockEntityRenderDispatcher dispatcher) {
		super(dispatcher);
	}

	@Override
	public final void render(T entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final World world = entity.getWorld();
		if (world == null) {
			return;
		}

		final BlockPos pos = entity.getPos();
		final BlockState state = world.getBlockState(pos);
		final Direction facing = IBlock.getStatePropertySafe(state, HorizontalFacingBlock.FACING);

		if (RenderTrains.shouldNotRender(pos, RenderTrains.maxTrainRenderDistance, facing)) {
			return;
		}

		final int arrowDirection = IBlock.getStatePropertySafe(state, IPropagateBlock.PROPAGATE_PROPERTY);
		final int glassLength = getGlassLength(world, pos, facing);
		final int prevArrowDirection = (int) (ClientData.DATA_CACHE.renderingStateMap.getOrDefault(pos, 0b100L) & 0b111);
		final int prevGlassLength = (int) (ClientData.DATA_CACHE.renderingStateMap.getOrDefault(pos, 0L) >> 3);

		final VertexConsumerProvider.Immediate immediate = RenderTrains.shouldNotRender(pos, RenderTrains.maxTrainRenderDistance / 4, null) ? null : VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());

		ClientData.DATA_CACHE.requestRenderForPos(matrices, vertexConsumers, immediate, pos, prevArrowDirection != arrowDirection || prevGlassLength != glassLength, () -> {
			final List<RenderingInstruction> renderingInstructions = new ArrayList<>();

			RenderingInstruction.addPush(renderingInstructions);
			RenderingInstruction.addTranslate(renderingInstructions, 0.5F, 0, 0.5F);
			RenderingInstruction.addRotateYDegrees(renderingInstructions, -facing.asRotation());

			renderAdditionalUnmodified(renderingInstructions, state, facing, light);

			final Platform platform = ClientData.getClosePlatform(pos);
			final RouteRenderer routeRenderer = new RouteRenderer(renderingInstructions, platform, false, false);

			RenderingInstruction.addTranslate(renderingInstructions, 0, 1, 0);
			RenderingInstruction.addRotateZDegrees(renderingInstructions, 180);
			RenderingInstruction.addTranslate(renderingInstructions, -0.5F, 0, getZ() - SMALL_OFFSET * 2);

			if (isLeft(state)) {
				if (glassLength > 1) {
					switch (getRenderType(world, pos, state)) {
						case ARROW:
							routeRenderer.renderArrow(getSidePadding() + EXTRA_PADDING, glassLength - getSidePadding() - EXTRA_PADDING, getTopPadding() + EXTRA_PADDING, 1 - getBottomPadding() - EXTRA_PADDING, (arrowDirection & 0b10) > 0, (arrowDirection & 0b01) > 0, facing, light);
							break;
						case ROUTE:
							final boolean flipLine = arrowDirection == 1;
							routeRenderer.renderLine(flipLine ? glassLength - getSidePadding() - EXTRA_PADDING * 2 : getSidePadding() + EXTRA_PADDING * 2, flipLine ? getSidePadding() + EXTRA_PADDING * 2 : glassLength - getSidePadding() - EXTRA_PADDING * 2, getTopPadding() + EXTRA_PADDING, 1 - getBottomPadding() - EXTRA_PADDING, getBaseScale(), facing, light);
							break;
					}
				}
			}

			renderAdditional(renderingInstructions, routeRenderer, state, facing, light);

			RenderingInstruction.addPop(renderingInstructions);
			return renderingInstructions;
		});

		ClientData.DATA_CACHE.renderingStateMap.put(pos, ((long) glassLength << 3) + arrowDirection);
	}

	protected void renderAdditionalUnmodified(List<RenderingInstruction> renderingInstructions, BlockState state, Direction facing, int light) {
	}

	protected abstract float getZ();

	protected abstract float getSidePadding();

	protected abstract float getBottomPadding();

	protected abstract float getTopPadding();

	protected abstract int getBaseScale();

	protected abstract boolean isLeft(BlockState state);

	protected abstract boolean isRight(BlockState state);

	protected abstract RenderType getRenderType(WorldAccess world, BlockPos pos, BlockState state);

	protected abstract void renderAdditional(List<RenderingInstruction> renderingInstructions, RouteRenderer routeRenderer, BlockState state, Direction facing, int light);

	private int getGlassLength(WorldAccess world, BlockPos pos, Direction facing) {
		int glassLength = 1;

		while (true) {
			final BlockState state = world.getBlockState(pos.offset(facing.rotateYClockwise(), glassLength));
			if (state.getBlock() == world.getBlockState(pos).getBlock() && !isLeft(state)) {
				glassLength++;
				if (isRight(state)) {
					break;
				}
			} else {
				break;
			}
		}

		return glassLength;
	}

	protected enum RenderType {ARROW, ROUTE, NONE}
}
