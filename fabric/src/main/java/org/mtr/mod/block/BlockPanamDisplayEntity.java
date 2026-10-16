package org.mtr.mod.block;

import org.mtr.mapping.holder.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/** Shared data/revision contract for the hanging display and the single-face screen. */
public abstract class BlockPanamDisplayEntity extends BlockPIDSBase.BlockEntityBase {
    private long pixelRevision;
    protected BlockPanamDisplayEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,BiPredicate<World,BlockPos> stores,BiFunction<World,BlockPos,BlockPos> owner){
        super(3,stores,owner,type,pos,state);
    }
    public long pixelRevision(){return pixelRevision;}
    protected void invalidatePixels(){pixelRevision++;}
    @Override public void readCompoundTag(CompoundTag tag){super.readCompoundTag(tag);invalidatePixels();}
    @Override public void setData(String[] messages,boolean[] hidden,LongAVLTreeSet platforms,int page){super.setData(messages,hidden,platforms,page);invalidatePixels();}
    @Override public boolean showArrivalNumber(){return false;}
    @Override public boolean alternateLines(){return false;}
    @Override public int textColorArrived(){return 0xFF9900;}
    @Override public int textColor(){return 0xFF9900;}
}
