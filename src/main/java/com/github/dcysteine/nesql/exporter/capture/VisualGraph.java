package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.VisualValues.record;

/** A typed producer's resource DAG. Links are explicit dependencies, never
 * placeholders in strings or arbitrary executable data in an image. */
final class VisualGraph {
    interface Writer { String asset(Images.Image image) throws Exception; void row(JsonObject row) throws Exception; }
    static Node node(JsonObject data){return new Node(data);}
    static String publish(Node root,Writer writer) throws Exception {
        return new Publisher(writer).publish(root);
    }
    /** The diagnostic and publisher share one bounded structural check. No
     * resource encoding, row binding or writes are needed to inspect a root. */
    static Inspection inspect(Node root) {
        long[] size=new long[3];
        validate(root,Collections.newSetFromMap(new IdentityHashMap<>()),new IdentityHashMap<>(),0,size);
        return new Inspection(size[0],size[1],size[2]);
    }
    static final class Inspection {
        final long nodes,images,pixels;
        private Inspection(long nodes,long images,long pixels){this.nodes=nodes;this.images=images;this.pixels=pixels;}
    }
    static final class Publisher {
        private final Writer writer;private final Map<Node,String> constants=new WeakHashMap<>();
        Publisher(Writer writer){this.writer=Objects.requireNonNull(writer);}
        String publish(Node root)throws Exception{
            inspect(root);
            return bind(root,writer,new IdentityHashMap<>(),new IdentityHashMap<>(),constants);
        }
    }
    static final class Node {
        private final JsonObject data;
        private final Map<String,Links> links=new LinkedHashMap<>();
        private boolean sealed;
        private Node(JsonObject data){this.data=copy(data);}
        Node link(String field,Node target){return add(field,new Links(Collections.singletonList(target),null,false));}
        Node links(String field,List<Node> targets){return add(field,new Links(new ArrayList<>(targets),null,true));}
        Node image(String field,Images.Image image){return add(field,new Links(null,Collections.singletonList(image),false));}
        Node images(String field,List<Images.Image> images){return add(field,new Links(null,new ArrayList<>(images),true));}
        private Node add(String field,Links link){
            if(sealed)throw new IllegalStateException("A published visual resource is immutable");
            if(field==null||!field.matches("[a-zA-Z][a-zA-Z0-9]{0,47}")||data.has(field)||links.putIfAbsent(field,link)!=null)
                throw new IllegalArgumentException("Duplicate/invalid visual binding");
            return this;
        }
    }
    private static final class Links {
        final List<Node> nodes;final List<Images.Image> images;final boolean array;
        Links(List<Node> nodes,List<Images.Image> images,boolean array){this.nodes=nodes;this.images=images;this.array=array;}
    }
    private static void validate(Node node,Set<Node> path,Map<Object,Boolean> seen,int depth,long[] size){
        Jobs.checkpoint();if(node==null||depth>64||!path.add(node))throw new IllegalArgumentException("Invalid/cyclic visual resource graph");
        try{
            if(seen.put(node,true)!=null)return;
            if(++size[0]>1024)throw new IllegalArgumentException("Visual dependency graph exceeds resource budget");
            for(Links links:node.links.values()){
                int count=links.nodes==null?links.images.size():links.nodes.size();
                if(!links.array&&count!=1)throw new IllegalArgumentException("Invalid scalar visual binding");
                if(links.nodes!=null)for(Node child:links.nodes)validate(child,path,seen,depth+1,size);
                if(links.images!=null)for(Images.Image image:links.images){
                    if(image==null||image.visual!=null)throw new IllegalArgumentException("A visual sampler cannot contain another preview program");
                    if(seen.put(image,true)==null){
                        size[1]++;
                        size[2]=Math.addExact(size[2],(long)image.pixels.getWidth()*image.pixels.getHeight());
                        if(size[2]>32L*1024*1024)throw new IllegalArgumentException("Visual resource closure exceeds pixel budget");
                    }
                }
            }
            node.sealed=true;
        }finally{path.remove(node);}
    }
    private static String bind(Node node,Writer writer,Map<Node,String> nodes,Map<Images.Image,String> images,Map<Node,String> constants) throws Exception {
        Jobs.checkpoint();String cached=nodes.get(node);if(cached!=null)return cached;
        if(node.links.isEmpty()){cached=constants.get(node);if(cached!=null)return cached;}
        JsonObject data=copy(node.data);
        for(Map.Entry<String,Links> binding:node.links.entrySet()){
            Links links=binding.getValue();JsonArray ids=new JsonArray();
            if(links.nodes!=null)for(Node child:links.nodes)ids.add(new com.google.gson.JsonPrimitive(bind(child,writer,nodes,images,constants)));
            if(links.images!=null)for(Images.Image image:links.images){
                String id=images.get(image);if(id==null){id=writer.asset(image);images.put(image,id);}ids.add(new com.google.gson.JsonPrimitive(id));
            }
            if(!links.array&&ids.size()!=1)throw new IllegalArgumentException("Invalid scalar visual binding");
            data.add(binding.getKey(),links.array?ids:ids.get(0));
        }
        JsonObject row=record(data);writer.row(row);String id=row.get("id").getAsString();nodes.put(node,id);if(node.links.isEmpty())constants.put(node,id);return id;
    }
    private static JsonObject copy(JsonObject data){return new com.google.gson.JsonParser().parse(data.toString()).getAsJsonObject();}
}
