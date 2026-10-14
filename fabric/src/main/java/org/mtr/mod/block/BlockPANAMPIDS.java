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
        return IBlock.getVoxelShapeByDirection(5.9, .75, 0, 10.1, 16, 16, IBlock.getStatePropertySafe(state, FACING));
    }

    @Nonnull
    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntity(pos, state);
    }

    public static class BlockEntity extends BlockEntityHorizontalBase {
        private long pixelRevision;
        public long pixelRevision() { return pixelRevision; }
        @Override public void readCompoundTag(CompoundTag tag) { super.readCompoundTag(tag); pixelRevision++; }
        @Override public void setData(String[] messages, boolean[] hidden, org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet platforms, int page) {
            super.setData(messages, hidden, platforms, page); pixelRevision++;
        }
        public BlockEntity(BlockPos pos, BlockState state) {
            super(3, BlockEntityTypes.PANAM_PIDS.get(), pos, state);
        }

        @Override
        public boolean showArrivalNumber() {
            return false;
        }
    }
}
