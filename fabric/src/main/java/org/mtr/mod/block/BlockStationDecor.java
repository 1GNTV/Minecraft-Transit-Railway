package org.mtr.mod.block;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mapping.tool.HolderBase;
import org.mtr.mod.*;
import org.mtr.mod.decor.*;
import org.mtr.mod.packet.PacketOpenBlockEntityScreen;
import java.util.List;

/** Brush-editable anchor. Extended collisions are supplied by the spatial index, including parts outside this anchor. */
public final class BlockStationDecor extends BlockWaterloggable implements DirectionHelper,BlockWithEntity {
    public final DecorKind kind;
    public BlockStationDecor(DecorKind kind){super(org.mtr.mod.Blocks.createDefaultBlockSettings(true,state->kind.light()?12:0).nonOpaque());this.kind=kind;}
    @Override public BlockState getPlacementState2(ItemPlacementContext ctx){return super.getPlacementState2(ctx).with(new Property<>(FACING.data),ctx.getPlayerFacing().getOpposite().data);}
    @Override public void addBlockProperties(List<HolderBase<?>> values){super.addBlockProperties(values);values.add(FACING);}
    @Override public VoxelShape getCollisionShape2(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return VoxelShapes.empty();}
    @Override public VoxelShape getCullingShape2(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
    @Override public VoxelShape getOutlineShape2(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return IBlock.getVoxelShapeByDirection(2,0,2,14,16,14,IBlock.getStatePropertySafe(state,FACING));}
    @Override public BlockRenderType getRenderType2(BlockState state){return BlockRenderType.getEntityblockAnimatedMapped();}
    @Override public BlockEntityExtension createBlockEntity(BlockPos pos,BlockState state){return new BlockEntity(pos,state,kind);}
    @Override public ActionResult onUse2(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
        if(kind==DecorKind.JOURNEY_KIOSK&&!player.isHolding(org.mtr.mod.Items.BRUSH.get().asItem())){if(!world.isClient())Init.REGISTRY.sendPacketToClient(ServerPlayerEntity.cast(player),new PacketOpenBlockEntityScreen(pos));return ActionResult.SUCCESS;}
        if(kind==DecorKind.WAYPOINT&&player.isSneaking())return IBlock.checkHoldingBrush(world,player,()->org.mtr.mod.packet.PacketBuilderWayfinding.select(ServerPlayerEntity.cast(player),pos));
        if(player.isSneaking())return ActionResult.PASS;
        return IBlock.checkHoldingBrush(world,player,()->Init.REGISTRY.sendPacketToClient(ServerPlayerEntity.cast(player),new PacketOpenBlockEntityScreen(pos)));
    }
    @Override public void onStateReplaced2(BlockState before,World world,BlockPos pos,BlockState after,boolean moved){
        if(kind==DecorKind.WAYPOINT&&!world.isClient()&&before.getBlock().data!=after.getBlock().data){var save=(org.mtr.mod.data.PersistentStateData)org.mtr.mapping.mapper.PersistenceStateExtension.register(new ServerWorld(world.data),org.mtr.mod.data.PersistentStateData::new,Init.MOD_ID);save.walking().remove(pos.asLong());save.walkingChanged();}
        super.onStateReplaced2(before,world,pos,after,moved);
    }
    public static final class BlockEntity extends BlockEntityExtension {
        private DecorSpec spec;
        public BlockEntity(BlockPos pos,BlockState state,DecorKind kind){super(BlockEntityTypes.STATION_DECOR.get(kind).get(),pos,state);spec=DecorSpec.defaults(kind);}
        public DecorSpec spec(){return spec;}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return DecorCollisionIndex.bounds(this); }
        public void setSpec(DecorSpec value){if(!spec.equals(value)){spec=value;DecorCollisionIndex.update(this);markDirty2();}}
        @Override public void readCompoundTag(CompoundTag tag){super.readCompoundTag(tag);spec=DecorSpec.read(tag,spec.kind());DecorCollisionIndex.update(this);}
        @Override public void writeCompoundTag(CompoundTag tag){super.writeCompoundTag(tag);spec.write(tag);}
    }
}
