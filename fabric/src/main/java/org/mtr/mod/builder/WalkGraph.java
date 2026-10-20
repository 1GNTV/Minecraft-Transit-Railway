package org.mtr.mod.builder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit walking topology. Connections are authored through corridors and landings, never guessed through walls. */
public final class WalkGraph {
    public record Node(long id,double x,double y,double z,long platform,String label,String kind,long revision,List<Long> links){
        public Node{
            if(!Double.isFinite(x+y+z)||Math.abs(x)>30000000||Math.abs(z)>30000000||Math.abs(y)>4096)throw new IllegalArgumentException("Position de cheminement invalide.");
            label=label==null?"":label.strip();if(label.length()>64)throw new IllegalArgumentException("Identification limitée à 64 caractères.");
            if(!Set.of("passage","platform","exit","stairs","lift").contains(kind))throw new IllegalArgumentException("Type de point invalide.");
            links=List.copyOf(new LinkedHashSet<>(links));if(links.size()>16)throw new IllegalArgumentException("16 liaisons maximum par point.");
        }
        public Node linked(long other,boolean add){var next=new ArrayList<>(links);if(add){if(!next.contains(other))next.add(other);}else next.remove(other);return new Node(id,x,y,z,platform,label,kind,revision+1,next);}
        public double distance(Node b){return Math.sqrt((x-b.x)*(x-b.x)+(y-b.y)*(y-b.y)+(z-b.z)*(z-b.z));}
    }
    private final LinkedHashMap<Long,Node> nodes=new LinkedHashMap<>();
    public Collection<Node> nodes(){return Collections.unmodifiableCollection(nodes.values());}
    public Node get(long id){return nodes.get(id);}
    public void put(Node node){if(!nodes.containsKey(node.id())&&nodes.size()>=4096)throw new IllegalArgumentException("4096 points maximum par dimension.");nodes.put(node.id(),node);}
    public void remove(long id){nodes.remove(id);for(var n:new ArrayList<>(nodes.values()))if(n.links().contains(id))put(n.linked(id,false));}
    public void link(long a,long b,boolean add){
        Node A=nodes.get(a),B=nodes.get(b);if(A==null||B==null||a==b)throw new IllegalArgumentException("Choisis deux points distincts.");
        if(add&&A.distance(B)>96)throw new IllegalArgumentException("Ajoute un point intermédiaire : liaison limitée à 96 blocs.");
        Node nextA=A.linked(b,add),nextB=B.linked(a,add);put(nextA);put(nextB);
    }
    public Node nearest(double x,double y,double z,double radius){Node best=null;double distance=radius*radius;for(Node n:nodes.values()){double d=(n.x()-x)*(n.x()-x)+(n.y()-y)*(n.y()-y)+(n.z()-z)*(n.z()-z);if(d<=distance){distance=d;best=n;}}return best;}
    public List<Node> path(long start,long platform,boolean accessible){return platform==0?List.of():search(start,n->n.platform()==platform,accessible);}
    public List<Node> pathToNode(long start,long destination,boolean accessible){return search(start,n->n.id()==destination,accessible);}
    private List<Node> search(long start,java.util.function.Predicate<Node> goal,boolean accessible){
        if(!nodes.containsKey(start))return List.of();
        record Step(long id,double distance){}
        PriorityQueue<Step> queue=new PriorityQueue<>(Comparator.comparingDouble(Step::distance));Map<Long,Double> distances=new HashMap<>();Map<Long,Long> previous=new HashMap<>();
        queue.add(new Step(start,0));distances.put(start,0.0);Long target=null;
        while(!queue.isEmpty()){
            Step current=queue.remove();if(current.distance()>distances.getOrDefault(current.id(),Double.POSITIVE_INFINITY))continue;Node n=nodes.get(current.id());
            if(goal.test(n)){target=n.id();break;}
            for(long id:n.links()){Node next=nodes.get(id);if(next==null||!next.links().contains(n.id())||accessible&&(next.kind().equals("stairs")||n.kind().equals("stairs")))continue;
                double cost=current.distance()+n.distance(next);if(cost<distances.getOrDefault(id,Double.POSITIVE_INFINITY)){distances.put(id,cost);previous.put(id,n.id());queue.add(new Step(id,cost));}
            }
        }
        if(target==null)return List.of();LinkedList<Node> result=new LinkedList<>();long at=target;while(true){result.addFirst(nodes.get(at));if(at==start)break;Long before=previous.get(at);if(before==null)return List.of();at=before;}return List.copyOf(result);
    }
    public WalkGraph near(double x,double y,double z,int limit){
        WalkGraph out=new WalkGraph();var seeds=nodes.values().stream().filter(n->Math.hypot(n.x()-x,n.z()-z)<=192&&Math.abs(n.y()-y)<=96).sorted(Comparator.comparingDouble(n->(n.x()-x)*(n.x()-x)+(n.y()-y)*(n.y()-y)+(n.z()-z)*(n.z()-z))).toList();
        for(Node seed:seeds){if(out.nodes.size()>=limit)break;if(out.nodes.containsKey(seed.id()))continue;Deque<Node> pending=new ArrayDeque<>();pending.add(seed);while(!pending.isEmpty()&&out.nodes.size()<limit){Node n=pending.removeFirst();if(out.nodes.containsKey(n.id()))continue;out.put(n);for(long id:n.links()){Node next=nodes.get(id);if(next!=null&&!out.nodes.containsKey(id))pending.addLast(next);}}}return out;
    }
    public String encode(){StringBuilder out=new StringBuilder();for(Node n:nodes.values()){
        out.append(n.id()).append(';').append(n.x()).append(';').append(n.y()).append(';').append(n.z()).append(';').append(n.platform()).append(';').append(Base64.getUrlEncoder().withoutPadding().encodeToString(n.label().getBytes(StandardCharsets.UTF_8))).append(';').append(n.kind()).append(';').append(n.revision()).append(';');
        StringJoiner links=new StringJoiner(",");n.links().forEach(id->links.add(id.toString()));out.append(links).append('\n');
    }return out.toString();}
    public static WalkGraph decode(String value){
        WalkGraph graph=new WalkGraph();if(value==null||value.isEmpty())return graph;if(value.length()>2000000)throw new IllegalArgumentException("Cheminements trop volumineux.");
        for(String row:value.split("\n")){String[] a=row.split(";",-1);if(a.length!=9)throw new IllegalArgumentException("Cheminement invalide.");List<Long> links=new ArrayList<>();if(!a[8].isEmpty())for(String id:a[8].split(","))links.add(Long.parseLong(id));graph.put(new Node(Long.parseLong(a[0]),Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3]),Long.parseLong(a[4]),new String(Base64.getUrlDecoder().decode(a[5]),StandardCharsets.UTF_8),a[6],Long.parseLong(a[7]),links));}return graph;
    }
}
