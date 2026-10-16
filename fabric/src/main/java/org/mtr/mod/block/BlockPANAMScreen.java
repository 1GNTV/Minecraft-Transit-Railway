package org.mtr.mod.block;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.tool.HolderBase;
import org.mtr.mod.BlockEntityTypes;
import org.mtr.mod.data.PanamScreenSettings;
import java.util.List;

/** One selectable anchor, one luminous face, no casing or second block. */
public final class BlockPANAMScreen extends BlockPIDSBase {
    public BlockPANAMScreen(){super(3,(world,pos)->true,(world,pos)->pos);}
    @Override public BlockState getPlacementState2(ItemPlacementContext ctx){return getDefaultState2().with(new Property<>(FACING.data),ctx.getPlayerFacing().data);}
    @Override public void addBlockProperties(List<HolderBase<?>> properties){properties.add(FACING);}
    @Override public VoxelShape getCollisionShape2(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return VoxelShapes.empty();}
    @Override public VoxelShape getCullingShape2(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
    @Override public VoxelShape getOutlineShape2(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
        return IBlock.getVoxelShapeByDirection(2,2,7.5,14,14,8.5,IBlock.getStatePropertySafe(state,FACING));
    }
    @Override public BlockRenderType getRenderType2(BlockState state){return BlockRenderType.getEntityblockAnimatedMapped();}
    @Override public BlockEntityExtension createBlockEntity(BlockPos pos,BlockState state){return new BlockEntity(pos,state);}
    public static final class BlockEntity extends BlockPanamDisplayEntity {
        private PanamScreenSettings settings=PanamScreenSettings.DEFAULT;
        public BlockEntity(BlockPos pos,BlockState state){super(BlockEntityTypes.PANAM_SCREEN.get(),pos,state,(world,p)->true,(world,p)->p);}
        public PanamScreenSettings settings(){return settings;}
        public void setSettings(PanamScreenSettings value){if(!settings.equals(value)){settings=value;invalidatePixels();markDirty2();}}
        @Override public void readCompoundTag(CompoundTag tag){
            super.readCompoundTag(tag);
            settings=tag.getInt("panam_screen_version")==0?PanamScreenSettings.DEFAULT:new PanamScreenSettings(
                tag.getFloat("panam_x"),tag.getFloat("panam_y"),tag.getFloat("panam_z"),
                tag.getFloat("panam_yaw"),tag.getFloat("panam_pitch"),tag.getFloat("panam_roll"),
                tag.getFloat("panam_width"),tag.getFloat("panam_height"),tag.getInt("panam_resolution_width"),tag.getInt("panam_resolution_height"),tag.getFloat("panam_zoom"));
        }
        @Override public void writeCompoundTag(CompoundTag tag){
            super.writeCompoundTag(tag);tag.putInt("panam_screen_version",1);
            tag.putFloat("panam_x",settings.x);tag.putFloat("panam_y",settings.y);tag.putFloat("panam_z",settings.z);
            tag.putFloat("panam_yaw",settings.yaw);tag.putFloat("panam_pitch",settings.pitch);tag.putFloat("panam_roll",settings.roll);
            tag.putFloat("panam_width",settings.width);tag.putFloat("panam_height",settings.height);
            tag.putInt("panam_resolution_width",settings.resolutionWidth);tag.putInt("panam_resolution_height",settings.resolutionHeight);tag.putFloat("panam_zoom",settings.zoom);
        }
    }
}
