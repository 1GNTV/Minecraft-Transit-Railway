package org.mtr.mod.builder;
import org.mtr.libraries.com.google.gson.*;
import org.mtr.core.tool.Utilities;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Local named resource-ID lists; never replaces unknown train resources with a different model. */
public final class ConsistPresets {
    private static final Path FILE=Path.of("config","sillon-consists.json");
    private ConsistPresets(){}
    public static Map<String,List<String>> load(String mode){Map<String,List<String>> out=new LinkedHashMap<>();try{if(!Files.exists(FILE)||Files.size(FILE)>262144)return out;JsonObject root=Utilities.parseJson(Files.readString(FILE)).getAsJsonObject();if(!root.has(mode))return out;for(var e:root.getAsJsonObject(mode).entrySet()){if(out.size()>=64)break;List<String> ids=new ArrayList<>();for(var id:e.getValue().getAsJsonArray())if(ids.size()<128&&id.getAsString().length()<256)ids.add(id.getAsString());out.put(e.getKey(),List.copyOf(ids));}}catch(Exception ignored){}return out;}
    public static void save(String mode,String name,List<String> ids)throws IOException{String clean=name.strip();if(clean.isEmpty()||clean.length()>64||ids.size()>128)throw new IOException("Nom ou composition invalide");Map<String,List<String>> presets=load(mode);if(presets.size()>=64&&!presets.containsKey(clean))throw new IOException("64 compositions maximum");presets.put(clean,List.copyOf(ids));write(mode,presets);}
    public static void delete(String mode,String name)throws IOException{var presets=load(mode);presets.remove(name);write(mode,presets);}
    private static void write(String mode,Map<String,List<String>> values)throws IOException{JsonObject root=new JsonObject();try{if(Files.exists(FILE)&&Files.size(FILE)<262144)root=Utilities.parseJson(Files.readString(FILE)).getAsJsonObject();}catch(RuntimeException ignored){}JsonObject group=new JsonObject();values.forEach((name,ids)->{JsonArray array=new JsonArray();ids.forEach(array::add);group.add(name,array);});root.add(mode,group);Files.createDirectories(FILE.getParent());Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");Files.writeString(tmp,root.toString());try{Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}}
}
