package org.mtr.mod.builder;

import org.mtr.core.data.*;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.core.serializer.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Compare-and-set on the simulation queue; only geometry/style fields are replaced. */
public final class BuilderRailRequest implements SerializedDataBase {
    public static final String KEY="sillon/builder/rail-edit";
    public String id="",expected="",shape="QUADRATIC",styles="[]",token="";public double radius;
    public BuilderRailRequest(Rail before,Rail after,String token){id=before.getHexId();expected=fingerprint(before);shape=after.railMath.getShape().name();radius=after.railMath.getVerticalRadius();styles=Utilities.getJsonObjectFromData(after).get("styles").toString();this.token=token;}
    public BuilderRailRequest(ReaderBase r){updateData(r);}
    @Override public void updateData(ReaderBase r){r.unpackString("id",v->id=v);r.unpackString("expected",v->expected=v);r.unpackString("shape",v->shape=v);r.unpackString("styles",v->styles=v);r.unpackString("token",v->token=v);r.unpackDouble("radius",v->radius=v);}
    @Override public void serializeData(WriterBase w){w.writeString("id",id);w.writeString("expected",expected);w.writeString("shape",shape);w.writeString("styles",styles);w.writeString("token",token);w.writeDouble("radius",radius);}
    public static String fingerprint(Rail r){return r.railMath.getShape().name()+":"+r.railMath.getVerticalRadius()+":"+r.getStyles().toString();}
    public Response apply(Simulator data){
        Rail current=data.railIdMap.get(id);
        if(current==null)return new Response(token,"La voie n’existe plus.","");
        if(!expected.equals(fingerprint(current)))return new Response(token,"Voie modifiée ailleurs : rouvrir l’éditeur.","");
        try{
            if(!Double.isFinite(radius)||radius<0||radius>current.railMath.getMaxVerticalRadius()+.001||styles.length()>8192)throw new IllegalArgumentException();
            var parsed=org.mtr.libraries.com.google.gson.JsonParser.parseString(styles).getAsJsonArray();if(parsed.size()>64)throw new IllegalArgumentException();
            ObjectArrayList<String> list=new ObjectArrayList<>();for(var e:parsed){String s=e.getAsString();if(s.length()>256)throw new IllegalArgumentException();list.add(s);}
            Rail replacement=Rail.copy(Rail.copy(current,Rail.Shape.valueOf(shape),radius),list);
            if(!replacement.isValid())return new Response(token,"Géométrie invalide : modification refusée.","");
            return new Response(token,"Modification enregistrée.",Utilities.getJsonObjectFromData(new UpdateDataRequest(data).addRail(replacement).update()).toString());
        }catch(RuntimeException e){return new Response(token,"Paramètres de voie invalides.","");}
    }
    public static final class Response implements SerializedDataBase {
        final String token,message,update;public Response(String t,String m,String u){token=t;message=m;update=u;}
        @Override public void updateData(ReaderBase r){}
        @Override public void serializeData(WriterBase w){w.writeString("token",token);w.writeString("message",message);w.writeString("update",update);}
    }
}
