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
        return IBlock.getVoxelShapeByDirection(5.2, -1, 0, 10.8, 16, 16, IBlock.getStatePropertySafe(state, FACING));
    }

    @Nonnull
    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntity(pos, state);
    }

    /** Chunk meshes can read this provider off-thread; the owner stores a volatile color. */
    public static int casingTint(BlockState state, BlockRenderView view, BlockPos pos, int tintIndex) {
        if (tintIndex != 0) return 0xFFFFFF;
        if (view == null || pos == null) return 0x808080;
        Direction facing = IBlock.getStatePropertySafe(state, FACING);
        BlockPos owner = facing == Direction.NORTH || facing == Direction.EAST ? pos : pos.offset(facing);
        org.mtr.mapping.holder.BlockEntity entity = view.getBlockEntity(owner);
        return entity != null && entity.data instanceof BlockEntity ? ((BlockEntity) entity.data).casingColor : 0x808080;
    }

    public static class BlockEntity extends BlockPanamDisplayEntity {
        private volatile int casingColor = 0x808080;
        public boolean setCasingColor(int color) {
            int rgb = color & 0xFFFFFF;
            if (casingColor == rgb) return false;
            casingColor = rgb;
            return true;
        }
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
