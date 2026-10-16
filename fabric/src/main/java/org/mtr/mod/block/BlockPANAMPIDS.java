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
        // Encloses the twin screens, projecting lip and ceiling suspension, while
        // retaining the existing two-block horizontal placement and state.
        return IBlock.getVoxelShapeByDirection(4.5, -.8, 0, 11.5, 16, 16, IBlock.getStatePropertySafe(state, FACING));
    }

    @Nonnull
    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntity(pos, state);
    }

    public static class BlockEntity extends BlockPanamDisplayEntity {
        public BlockEntity(BlockPos pos, BlockState state) {
            super(BlockEntityTypes.PANAM_PIDS.get(), pos, state,
                (world,p)->{Direction f=IBlock.getStatePropertySafe(world,p,FACING);return f==Direction.NORTH||f==Direction.EAST;},
                (world,p)->{Direction f=IBlock.getStatePropertySafe(world,p,FACING);return f==Direction.NORTH||f==Direction.EAST?p:p.offset(f);});
        }

        @Override
        public boolean showArrivalNumber() {
            return false;
        }
    }
}
