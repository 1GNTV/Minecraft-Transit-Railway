package org.mtr.mod.block;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mapping.mapper.DirectionHelper;
import org.mtr.mapping.tool.HolderBase;
import org.mtr.mod.BlockEntityTypes;
import org.mtr.mod.Blocks;
import org.mtr.mod.Init;
import org.mtr.mod.data.SillonSignageSpec;
import org.mtr.mod.packet.PacketOpenBlockEntityScreen;
import java.util.List;

/** Universal wall / ceiling / suspended Sillon signage anchor. */
public final class BlockSillonSignage extends BlockWaterloggable implements DirectionHelper, BlockWithEntity {
    public BlockSillonSignage(){super(Blocks.createDefaultBlockSettings(true).nonOpaque());}
    @Override public BlockState getPlacementState2(ItemPlacementContext ctx){
        return super.getPlacementState2(ctx).with(new Property<>(FACING.data),ctx.getPlayerFacing().data);
    }
    @Override public void addBlockProperties(List<HolderBase<?>> values){super.addBlockProperties(values);values.add(FACING);}
    @Override public VoxelShape getCollisionShape2(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return VoxelShapes.empty();}
    @Override public VoxelShape getCullingShape2(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
    @Override public VoxelShape getOutlineShape2(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
        return IBlock.getVoxelShapeByDirection(1,3,7.5,15,13,8.5,IBlock.getStatePropertySafe(state,FACING));
    }
    @Override public BlockRenderType getRenderType2(BlockState state){return BlockRenderType.getEntityblockAnimatedMapped();}
    @Override public BlockEntityExtension createBlockEntity(BlockPos pos,BlockState state){return new BlockEntity(pos,state);}
    @Override public ActionResult onUse2(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
        return IBlock.checkHoldingBrush(world,player,()->{
            var be=world.getBlockEntity(pos);
            if(be!=null&&be.data instanceof BlockEntity){
                Init.REGISTRY.sendPacketToClient(ServerPlayerEntity.cast(player),new PacketOpenBlockEntityScreen(pos));
            }
        });
    }
    public static final class BlockEntity extends BlockEntityExtension {
        private SillonSignageSpec document=SillonSignageSpec.DEFAULT;
        public BlockEntity(BlockPos pos,BlockState state){super(BlockEntityTypes.SILLON_SIGNAGE.get(),pos,state);}
        public SillonSignageSpec document(){return document;}
        public void setDocument(SillonSignageSpec value){
            if(!document.equals(value)){document=value;markDirty2();}
        }
        @Override public void readCompoundTag(CompoundTag tag){super.readCompoundTag(tag);document=SillonSignageSpec.read(tag);}
        @Override public void writeCompoundTag(CompoundTag tag){super.writeCompoundTag(tag);document.write(tag);}
    }
}
