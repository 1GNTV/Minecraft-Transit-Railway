package org.mtr.mod.chantier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.packet.PacketChantier;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.mtr.mod.chantier.ChantierPlan.*;

/** Server-authoritative incremental construction. Never edits from the render or simulation threads. */
public final class ChantierServer {
    private static final Map<UUID,Job> JOBS=new LinkedHashMap<>();
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Sillon chantier journal");t.setDaemon(true);return t;});
    private static final TagKey<Block> ORES=TagKey.create(Registries.BLOCK,new ResourceLocation("forge","ores"));
    private static final TagKey<Block> C_ORES=TagKey.create(Registries.BLOCK,new ResourceLocation("c","ores"));
    private static final Set<Block> NATURAL=Set.of(Blocks.AIR,Blocks.CAVE_AIR,Blocks.VOID_AIR,Blocks.WATER,Blocks.LAVA,Blocks.GRAVEL,Blocks.SAND,Blocks.RED_SAND,Blocks.CLAY,Blocks.CALCITE,Blocks.TUFF,Blocks.DRIPSTONE_BLOCK,Blocks.POINTED_DRIPSTONE,Blocks.SNOW,Blocks.SNOW_BLOCK,Blocks.ICE,Blocks.PACKED_ICE,Blocks.BLUE_ICE,Blocks.MOSS_BLOCK,Blocks.MOSS_CARPET,Blocks.GLOW_LICHEN,Blocks.SEAGRASS,Blocks.TALL_SEAGRASS);
    private record Saved(String dimension,String plan,Map<BlockPos,BlockState> original,Map<BlockPos,BlockState> before,Map<BlockPos,BlockState> after,boolean pending,String network,String desired){}
    public static void request(ServerPlayer p,int action,String project,String raw){
        if(!p.hasPermissions(2)||!p.isCreative()||!new org.mtr.mapping.holder.PlayerEntity(p).isHolding(org.mtr.mod.Items.CHANTIER.get().asItem())){reply(p,project,"Opérateur en créatif avec l’outil Chantier requis.",false,0);return;}
        try{UUID.fromString(project);if(action==2){Job j=JOBS.get(p.getUUID());if(j!=null&&j.project.equals(project)){if(j.phase==8||j.phase==6)reply(p,project,"Finalisation MTR en cours ; utilise la restauration une fois terminée.",true,99);else j.cancel=true;}return;}
            if(JOBS.containsKey(p.getUUID()))throw new IllegalArgumentException("Un chantier est déjà en cours.");if(JOBS.size()>=2)throw new IllegalArgumentException("Deux chantiers travaillent déjà : réessaie après leur fin.");
            if(action!=0&&action!=1)throw new IllegalArgumentException("Action inconnue.");
            ChantierPlan plan=action==0?ChantierPlan.decode(raw):null;JOBS.put(p.getUUID(),new Job(p,project,plan));
        }catch(Exception e){reply(p,project,e.getMessage()==null?"Plan invalide.":e.getMessage(),false,0);}
    }
    private static void reply(ServerPlayer p,String project,String text,boolean busy,int progress){Init.REGISTRY.sendPacketToClient(new org.mtr.mapping.holder.ServerPlayerEntity(p),new PacketChantier(100,project,(busy?"1":"0")+";"+progress+";"+text));}
    public static void tick(){Iterator<Job> it=JOBS.values().iterator();while(it.hasNext()){Job j=it.next();try{if(j.tick())it.remove();}catch(Throwable e){if(e instanceof IllegalArgumentException)Init.LOGGER.info("Chantier refused: {}",e.getMessage());else Init.LOGGER.error("Sillon chantier failed",e);j.fail(e.getMessage()==null?"Erreur de chantier.":e.getMessage());if(!j.wrote)it.remove();}}}
    public static void stop(){JOBS.clear();}
    private static boolean natural(BlockState s){return s.isAir()||s.canBeReplaced()||NATURAL.contains(s.getBlock())||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.BASE_STONE_NETHER)||s.is(BlockTags.DIRT)||s.is(BlockTags.TERRACOTTA)||s.is(ORES)||s.is(C_ORES);}
    private static BlockState material(String id){ResourceLocation key=new ResourceLocation(id);if(!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalArgumentException("Matériau absent : "+id);BlockState s=BuiltInRegistries.BLOCK.get(key).defaultBlockState();if(s.hasBlockEntity()||!s.getFluidState().isEmpty()||!s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO))throw new IllegalArgumentException("Choisis un matériau plein et solide : "+id);return s;}
    private static class Job {
        final ServerPlayer player;final ServerLevel level;final String project;final ChantierPlan plan;final Path file;
        final Map<BlockPos,BlockState> original=new LinkedHashMap<>(),before=new LinkedHashMap<>(),after=new LinkedHashMap<>();
        final List<BlockPos> applied=new ArrayList<>();
        net.minecraft.world.phys.AABB claim;int minClaimX=Integer.MAX_VALUE,minClaimY=Integer.MAX_VALUE,minClaimZ=Integer.MAX_VALUE,maxClaimX=Integer.MIN_VALUE,maxClaimY=Integer.MIN_VALUE,maxClaimZ=Integer.MIN_VALUE;ChantierRaster raster;Saved previous;List<ChantierRaster.Voxel> voxels;List<BlockPos> scan;Iterator<BlockPos> verify;int phase,index,total;long lastReply;boolean cancel,wrote,rollback;String failure="";
        CompletableFuture<Saved> loading;CompletableFuture<Void> saving;CompletableFuture<ChantierNetwork.Result> network;String networkSnapshot="{}",networkDesired="{}";Map<Role,BlockState> materials;
        Job(ServerPlayer p,String id,ChantierPlan plan){player=p;level=p.serverLevel();project=id;this.plan=plan;
            file=level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/sillon-chantier").resolve(p.getUUID().toString()).resolve(id+".dat");
            loading=CompletableFuture.supplyAsync(()->{try{return Files.exists(file)?load(file):null;}catch(Exception e){throw new CompletionException(e);}},IO);
            if(plan!=null){raster=new ChantierRaster(plan);materials=Map.of(Role.AIR,Blocks.AIR.defaultBlockState(),Role.WALL,material(plan.wall()),Role.FLOOR,material(plan.floor()),Role.ROOF,material(plan.roof()),Role.PLATFORM,material(plan.platform()),Role.PILLAR,material(plan.wall()),Role.LIGHT,Blocks.SEA_LANTERN.defaultBlockState(),Role.STAIR,material(plan.floor()));}
            reply(p,id,"Calcul du chantier…",true,0);
        }
        void fail(String text){failure=text;cancel=true;if(!wrote)reply(player,project,text,false,0);}
        boolean tick(){
            long until=System.nanoTime()+3000000;int budget=768;
            if(cancel&&!wrote&&phase<3){reply(player,project,"Chantier arrêté. "+failure,false,0);return true;}
            if(cancel&&!wrote&&saving!=null&&!saving.isDone())return false;
            if(cancel&&(wrote||saving!=null)&&!rollback){rollback=true;phase=5;applied.sort(Comparator.comparingInt((BlockPos p)->restoreOrder(before.get(p))).reversed());index=applied.size()-1;reply(player,project,"Restauration de l’opération interrompue…",true,0);}
            if(player.isRemoved()&&!wrote)return true;
            if(phase==0){if(!loading.isDone())return false;previous=loading.join();if(previous!=null&&!previous.dimension.equals(level.dimension().location().toString()))throw new IllegalArgumentException("Ce chantier appartient à une autre dimension.");if(previous!=null&&previous.pending&&plan!=null)throw new IllegalArgumentException("Chantier interrompu : restaure d’abord le terrain.");
                network=network(plan==null?3:0,previous==null?"{}":previous.network,previous==null?"{}":previous.desired);phase=7;return false;}
            if(phase==7){if(!network.isDone())return false;var result=network.join();if(!result.error.isEmpty())throw new IllegalArgumentException(result.error);networkSnapshot=result.snapshot;networkDesired=result.target;
                if(plan==null){if(previous==null)throw new IllegalArgumentException("Aucun chantier enregistré à annuler.");original.putAll(previous.original);after.putAll(previous.original);scan=new ArrayList<>(after.keySet());phase=2;}else phase=1;return false;}
            if(phase==1){if(!raster.step(12000))return false;voxels=raster.ordered();if(previous!=null){original.putAll(previous.original);after.putAll(previous.original);}for(var v:voxels){var c=v.cell();after.put(new BlockPos(c.x(),c.y(),c.z()),voxelMaterial(v));}for(long[] node:ChantierNetwork.nodes(networkDesired))after.put(new BlockPos((int)node[0],(int)node[1],(int)node[2]),nodeState(node));if(after.size()>MAX_CELLS)throw new IllegalArgumentException("L’historique du chantier dépasse "+MAX_CELLS+" blocs : termine puis crée un autre chantier.");scan=new ArrayList<>(after.keySet());phase=2;index=0;return false;}
            if(phase==2){
                while(index<scan.size()&&budget-->0&&System.nanoTime()<until){BlockPos pos=scan.get(index++);minClaimX=Math.min(minClaimX,pos.getX());minClaimY=Math.min(minClaimY,pos.getY());minClaimZ=Math.min(minClaimZ,pos.getZ());maxClaimX=Math.max(maxClaimX,pos.getX());maxClaimY=Math.max(maxClaimY,pos.getY());maxClaimZ=Math.max(maxClaimZ,pos.getZ());if(pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight())throw new IllegalArgumentException("Le chantier dépasse la hauteur du monde.");if(player.distanceToSqr(pos.getX(),pos.getY(),pos.getZ())>320*320)throw new IllegalArgumentException("Approche-toi du chantier (320 m maximum).");if(!level.hasChunkAt(pos))throw new IllegalArgumentException("Une partie du chantier n’est pas chargée : rapproche-toi ou réduis le tracé.");if(!level.mayInteract(player,pos))throw new IllegalArgumentException("Zone protégée à "+pos.toShortString());BlockState state=level.getBlockState(pos),target=after.get(pos);boolean known=previous!=null&&previous.after.containsKey(pos);
                    if(known){BlockState expected=previous.after.get(pos);boolean recovery=previous.pending&&state.equals(previous.before.get(pos));if(!state.equals(expected)&&!recovery){if(plan!=null&&target.equals(expected)&&!natural(state)){after.put(pos,state);before.put(pos,state);original.remove(pos);continue;}throw new IllegalArgumentException("Un bloc a changé depuis le chantier : "+pos.toShortString()+". Aucune modification.");}}
                    else if(!state.equals(target)&&(!natural(state)||state.getDestroySpeed(level,pos)<0||level.getBlockEntity(pos)!=null)){
                        if(previous!=null&&target.isAir()&&state.getDestroySpeed(level,pos)>=0){after.put(pos,state);before.put(pos,state);original.remove(pos);continue;}
                        if(state.getBlock() instanceof BlockNode&&target.isAir()){after.put(pos,state);target=state;}else throw new IllegalArgumentException("Construction ou bloc technique protégé : "+pos.toShortString()+". Déplace le volume.");
                    }
                    before.put(pos,state);if(!state.equals(target)||known)original.putIfAbsent(pos,state);
                }
                progress("Analyse du terrain",index,scan.size());if(index<scan.size())return false;
                claim=new net.minecraft.world.phys.AABB(minClaimX,minClaimY,minClaimZ,maxClaimX+1,maxClaimY+1,maxClaimZ+1);for(Job other:JOBS.values())if(other!=this&&other.level==level&&other.claim!=null&&claim.intersects(other.claim))throw new IllegalArgumentException("Un autre chantier travaille dans ce volume : attends sa fin.");
                // Stable envelope first, air excavation second, furniture last. Removed old cells restore their original terrain last.
                List<BlockPos> ordered=new ArrayList<>();Set<BlockPos> seen=new HashSet<>();if(voxels!=null)for(var v:voxels){var c=v.cell();BlockPos pos=new BlockPos(c.x(),c.y(),c.z());if(!before.get(pos).equals(after.get(pos))){ordered.add(pos);seen.add(pos);}}
                for(BlockPos pos:scan)if(!seen.contains(pos)&&!before.get(pos).equals(after.get(pos)))ordered.add(pos);
                if(plan==null)ordered.sort(Comparator.comparingInt(p->restoreOrder(after.get(p))));scan=ordered;total=scan.size();index=0;
                Saved snapshot=new Saved(level.dimension().location().toString(),plan==null?previous.plan:plan.encode(),Map.copyOf(original),Map.copyOf(before),Map.copyOf(after),true,networkSnapshot,networkDesired);
                saving=CompletableFuture.runAsync(()->save(file,snapshot),IO);phase=3;return false;
            }
            if(phase==3){if(!saving.isDone())return false;saving.join();phase=4;verify=before.keySet().iterator();return false;}
            if(phase==4){
                // Verify each block again immediately before mutation. No operation silently overwrites another builder.
                while(index<scan.size()&&budget-->0&&System.nanoTime()<until){BlockPos pos=scan.get(index);if(!level.hasChunkAt(pos)){failure="Un chunk a été déchargé.";cancel=true;return false;}if((!level.getBlockState(pos).equals(before.get(pos))&&!(plan==null&&sameFluid(level.getBlockState(pos),after.get(pos))))||(level.getBlockEntity(pos)!=null&&!(level.getBlockState(pos).getBlock() instanceof BlockNode))){failure="Modification concurrente à "+pos.toShortString()+" : "+level.getBlockState(pos)+" au lieu de "+before.get(pos);cancel=true;return false;}
                    if(!level.setBlock(pos,after.get(pos),2|16)&&!level.getBlockState(pos).equals(after.get(pos))){failure="Placement refusé à "+pos.toShortString();cancel=true;return false;}applied.add(pos);wrote=true;index++;
                }
                progress("Construction",index,total);if(index<scan.size())return false;
                verify=after.keySet().iterator();phase=9;return false;
            }
            if(phase==9){while(verify.hasNext()&&budget-->0&&System.nanoTime()<until){BlockPos pos=verify.next();if(!level.hasChunkAt(pos)||!level.getBlockState(pos).equals(after.get(pos))){failure="Le volume a changé pendant les travaux : "+pos.toShortString();cancel=true;return false;}}if(verify.hasNext())return false;network=network(plan==null?2:1,networkSnapshot,networkDesired);phase=8;return false;}
            if(phase==8){if(!network.isDone())return false;var result=network.join();if(!result.error.isEmpty()){failure=result.error;cancel=true;return false;}networkSnapshot=result.snapshot;broadcast(result);
                Saved complete=new Saved(level.dimension().location().toString(),plan==null?previous.plan:plan.encode(),Map.copyOf(original),Map.copyOf(before),Map.copyOf(after),false,networkSnapshot,networkDesired);
                saving=CompletableFuture.runAsync(()->{if(plan==null)delete(file);else save(file,complete);},IO);phase=6;return false;
            }
            if(phase==5){while(index>=0&&budget-->0&&System.nanoTime()<until){BlockPos pos=applied.get(index--);if(!level.hasChunkAt(pos)){index++;return false;}if(level.getBlockState(pos).equals(after.get(pos))||sameFluid(level.getBlockState(pos),before.get(pos)))level.setBlock(pos,before.get(pos),2|16);else failure="Restauration partielle : un autre joueur a modifié "+pos.toShortString();}
                if(index>=0)return false;saving=CompletableFuture.runAsync(()->{if(previous==null)delete(file);else save(file,previous);},IO);phase=6;return false;
            }
            if(phase==6){if(!saving.isDone())return false;saving.join();reply(player,project,rollback?"Opération restaurée. "+failure:plan==null?"Terrain initial restauré.":"Gare construite : "+total+" blocs modifiés. Tu peux ajuster le plan ou annuler le chantier.",false,100);return true;}
            return false;
        }
        BlockState nodeState(long[] node){var state=org.mtr.mod.block.BlockNode.getStateWithAngle(new org.mtr.mapping.holder.BlockState(org.mtr.mod.Blocks.RAIL_NODE.get().data.defaultBlockState()),node[3]/1000F);return state.with(new org.mtr.mapping.holder.Property<>(org.mtr.mod.block.BlockNode.IS_CONNECTED.data),true).data;}
        BlockState voxelMaterial(ChantierRaster.Voxel v){if(v.role()!=Role.STAIR)return materials.get(v.role());Part nearest=plan.parts().stream().filter(p->p.kind()==Kind.STAIRS).min(Comparator.comparingDouble(p->Math.hypot(p.a().x()-v.cell().x(),p.a().z()-v.cell().z()))).orElseThrow();double dx=nearest.b().x()-nearest.a().x(),dz=nearest.b().z()-nearest.a().z();net.minecraft.core.Direction direction=Math.abs(dx)>Math.abs(dz)?(dx>0?net.minecraft.core.Direction.EAST:net.minecraft.core.Direction.WEST):(dz>0?net.minecraft.core.Direction.SOUTH:net.minecraft.core.Direction.NORTH);if(nearest.b().y()<nearest.a().y())direction=direction.getOpposite();return Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,direction);}
        CompletableFuture<ChantierNetwork.Result> network(int action,String snapshot,String desired){CompletableFuture<ChantierNetwork.Result> result=new CompletableFuture<>();Init.sendMessageC2S(ChantierNetwork.KEY,new org.mtr.mapping.holder.MinecraftServer(level.getServer()),new org.mtr.mapping.holder.World(level),new ChantierNetwork(action,project,plan==null?"":plan.encode(),snapshot,desired),result::complete,ChantierNetwork.Result.class);return result;}
        void broadcast(ChantierNetwork.Result result){for(String raw:List.of(result.deleted,result.update)){if(raw.isEmpty())continue;int action=raw.equals(result.deleted)?102:101;if(raw.length()<26000){for(var viewer:level.players())Init.REGISTRY.sendPacketToClient(new org.mtr.mapping.holder.ServerPlayerEntity(viewer),new PacketChantier(action,project,raw));continue;}org.mtr.libraries.com.google.gson.JsonObject json=org.mtr.libraries.com.google.gson.JsonParser.parseString(raw).getAsJsonObject();for(var field:json.entrySet().stream().sorted(Comparator.comparingInt(e->e.getKey().equals("rails")?0:e.getKey().equals("platforms")?1:2)).toList())if(field.getValue().isJsonArray())for(var item:field.getValue().getAsJsonArray()){var one=new org.mtr.libraries.com.google.gson.JsonObject();var list=new org.mtr.libraries.com.google.gson.JsonArray();list.add(item);one.add(field.getKey(),list);for(var viewer:level.players())Init.REGISTRY.sendPacketToClient(new org.mtr.mapping.holder.ServerPlayerEntity(viewer),new PacketChantier(action,project,one.toString()));}}}
        void progress(String label,int value,int max){long now=System.currentTimeMillis();if(now-lastReply>500){lastReply=now;reply(player,project,label+" · "+value+" / "+max,true,max==0?100:value*100/max);}}
    }
    private static int restoreOrder(BlockState state){return state.getFluidState().isEmpty()?(state.isAir()?1:0):2;}
    private static boolean sameFluid(BlockState a,BlockState b){return !a.getFluidState().isEmpty()&&!b.getFluidState().isEmpty()&&a.getFluidState().getType().isSame(b.getFluidState().getType());}
    private static void delete(Path p){try{Files.deleteIfExists(p);}catch(Exception e){throw new CompletionException(e);}}
    private static void save(Path file,Saved data){try{
        CompoundTag root=new CompoundTag();root.putInt("version",1);root.putString("dimension",data.dimension);root.putString("plan",data.plan);root.putBoolean("pending",data.pending);root.putString("network",data.network);root.putString("desired",data.desired);
        Map<BlockState,Integer> ids=new LinkedHashMap<>();List<BlockPos> positions=new ArrayList<>(data.original.keySet());long[] pos=new long[positions.size()];int[] original=new int[pos.length],before=new int[pos.length],after=new int[pos.length];
        for(int n=0;n<pos.length;n++){BlockPos p=positions.get(n);pos[n]=p.asLong();original[n]=ids.computeIfAbsent(data.original.get(p),s->ids.size());before[n]=ids.computeIfAbsent(data.before.get(p),s->ids.size());after[n]=ids.computeIfAbsent(data.after.get(p),s->ids.size());}
        ListTag palette=new ListTag();ids.keySet().forEach(s->palette.add(NbtUtils.writeBlockState(s)));root.put("palette",palette);root.putLongArray("positions",pos);root.putIntArray("original",original);root.putIntArray("before",before);root.putIntArray("after",after);
        Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");NbtIo.writeCompressed(root,temp.toFile());try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }catch(Exception e){throw new CompletionException(e);}}
    private static Saved load(Path file)throws Exception{
        CompoundTag t=NbtIo.readCompressed(file.toFile());if(t.getInt("version")!=1)throw new IllegalArgumentException("Version du journal inconnue.");long[] positions=t.getLongArray("positions");int[] original=t.getIntArray("original"),before=t.getIntArray("before"),after=t.getIntArray("after");if(positions.length>MAX_CELLS||positions.length!=original.length||positions.length!=before.length||positions.length!=after.length)throw new IllegalArgumentException("Journal invalide.");
        List<BlockState> states=new ArrayList<>();for(var raw:t.getList("palette",10)){CompoundTag entry=(CompoundTag)raw;if(!BuiltInRegistries.BLOCK.containsKey(new ResourceLocation(entry.getString("Name"))))throw new IllegalArgumentException("Un matériau du journal n’est plus installé : "+entry.getString("Name"));states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),entry));}Map<BlockPos,BlockState> a=new LinkedHashMap<>(),b=new LinkedHashMap<>(),c=new LinkedHashMap<>();for(int n=0;n<positions.length;n++){BlockPos p=BlockPos.of(positions[n]);a.put(p,states.get(original[n]));b.put(p,states.get(before[n]));c.put(p,states.get(after[n]));}return new Saved(t.getString("dimension"),t.getString("plan"),a,b,c,t.getBoolean("pending"),t.contains("network")?t.getString("network"):"{}",t.contains("desired")?t.getString("desired"):"{}");
    }
}
