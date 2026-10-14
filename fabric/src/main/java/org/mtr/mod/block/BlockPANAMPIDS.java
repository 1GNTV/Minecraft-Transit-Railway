package org.mtr.mod.block;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mod.BlockEntityTypes;

import javax.annotation.Nonnull;

/**
 * Two-block PANAM station display. Reuses MTR's existing horizontal PIDS
 * placement, configuration packet and platform selection system.
 */
public class BlockPANAMPIDS extends BlockPIDSHorizontalBase {
    public BlockPANAMPIDS() {
        super(3);
    }

    @Nonnull
    @Override
    public VoxelShape getOutlineShape2(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return IBlock.getVoxelShapeByDirection(6.5, 1, 0, 9.5, 16, 16, IBlock.getStatePropertySafe(state, FACING));
    }

    @Nonnull
    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntity(pos, state);
    }

    public static class BlockEntity extends BlockEntityHorizontalBase {
        public BlockEntity(BlockPos pos, BlockState state) {
            super(3, BlockEntityTypes.PANAM_PIDS.get(), pos, state);
        }

        @Override
        public boolean showArrivalNumber() {
            return false;
        }
    }
}
