package org.mtr.mod.client;
import org.mtr.core.data.Position;
import org.mtr.core.tool.Utilities;
import org.mtr.mod.builder.NetworkDiagnostics.Problem;
import java.util.*;
public final class BuilderDiagnosticsClient {
    public static List<Problem> issues=List.of();public static long received;
    public static void accept(String raw){try{var list=Utilities.parseJson(raw).getAsJsonObject().getAsJsonArray("issues");List<Problem> next=new ArrayList<>();for(var e:list){var o=e.getAsJsonObject();next.add(new Problem(o.get("title").getAsString(),o.get("detail").getAsString(),o.has("a")?point(o.getAsJsonArray("a")):null,o.has("b")?point(o.getAsJsonArray("b")):null));}issues=List.copyOf(next);received=System.currentTimeMillis();}catch(RuntimeException ignored){}}
    private static Position point(org.mtr.libraries.com.google.gson.JsonArray a){return new Position(a.get(0).getAsLong(),a.get(1).getAsLong(),a.get(2).getAsLong());}
    private BuilderDiagnosticsClient(){}
}
