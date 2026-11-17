package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeCellQueries.call;
import static com.github.dcysteine.nesql.exporter.capture.NativeUsageReferences.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Native manual content and navigation, without opening a screen or constructing image renderers. */
final class NativeOcManual implements NativeUsageReferences.Source {
    private static final Pattern LINK=Pattern.compile("(!?)\\[([^\\]]*)\\]\\(([^)]+)\\)");
    private static final String IMAGE="li.cil.oc.client.renderer.markdown.segment.render.";
    private Object manual(){return NativeOcQueries.api("manual","li.cil.oc.client.Manual$");}
    public Object[] roots(){return roots(true);}
    public Object[] registrationRoots(){return roots(false);}
    private Object[] roots(boolean admit){
        Object manual=manual();List<Object> roots=new ArrayList<>();roots.add(manual);NativeOcQueries.itemRoots(roots);
        for(String name:Arrays.asList("pathProviders","contentProviders","imageProviders","tabs")){
            Object buffer=call(manual,name);NativeOcQueries.bind(roots,buffer);
            for(Object provider:NativeOcQueries.sequence(buffer)){
                if(admit&&name.equals("pathProviders"))NativeUsageAdmission.admit(provider,"path");
                if(name.equals("contentProviders")){
                    if(admit){NativeUsageAdmission.admit(provider,"content");NativeCellQueries.exact(provider,"li.cil.oc.api.prefab.ResourceContentProvider");}
                    if(provider!=null&&provider.getClass().getName().equals("li.cil.oc.api.prefab.ResourceContentProvider")){
                        roots.add(field(provider,"resourceDomain"));roots.add(field(provider,"basePath"));
                    }
                }
                if(name.equals("imageProviders")){
                    if(admit)imageProvider(provider,roots);
                    else if(provider!=null&&provider.getClass().getName().equals("scala.Tuple2")){roots.add(call(provider,"_1"));roots.add(call(provider,"_2"));}
                }
                if(name.equals("tabs")){
                    if(admit)NativeCellQueries.exact(provider,"li.cil.oc.client.Manual$Tab");
                    if(provider!=null&&provider.getClass().getName().equals("li.cil.oc.client.Manual$Tab"))roots.add(call(provider,"path"));
                }
            }
        }
        int count=NativeCellQueries.panelSize();roots.add(ItemList.items);roots.add(count);roots.add(ItemList.loadFinished);
        return roots.toArray();
    }
    private static String imageProvider(Object tuple,List<Object> roots){
        NativeCellQueries.exact(tuple,"scala.Tuple2");Object prefix=call(tuple,"_1"),provider=call(tuple,"_2");
        if(!(prefix instanceof String)||provider==null)throw fault("Invalid OC manual image provider");
        String kind=provider.getClass().getName();
        if(!Arrays.asList(IMAGE+"TextureImageProvider$",IMAGE+"ItemImageProvider$",IMAGE+"BlockImageProvider$",IMAGE+"OreDictImageProvider$").contains(kind))throw fault("Unadapted OC manual image provider: "+kind);
        roots.add(prefix);roots.add(provider);return kind;
    }
    public Data read(String locale){
        roots();Object manual=manual();List<Object> providers=NativeOcQueries.sequence(call(manual,"pathProviders"));
        Data data=new Data("ocManual","1.11.20-GTNH");List<ItemStack> panel=NativeCellQueries.panel();data.scanned=panel.size();
        LinkedHashSet<String> wanted=new LinkedHashSet<>();
        for(Object tab:NativeOcQueries.sequence(call(manual,"tabs")))wanted.add(normalize((String)call(tab,"path")));
        wanted.add("%LANGUAGE%/index.md");
        for(int index=0;index<panel.size();index++){
            Jobs.checkpoint();ItemStack input=Data.owned(panel.get(index));if(!data.query(input))continue;
            String path=null;
            for(Object provider:providers){NativeUsageAdmission.admit(provider,"path");path=(String)call(provider,"pathFor",new Class<?>[]{ItemStack.class},input.copy());if(path!=null)break;}
            if(path==null){data.unmatched++;continue;}
            path=normalize(path);wanted.add(path);data.observation(index,input,object("kind","manual","path",path,"document",key(path)));
        }
        Map<String,JsonObject> resources=new LinkedHashMap<>();Set<String> visited=new HashSet<>();
        List<String> queue=new ArrayList<>(wanted);
        for(int cursor=0;cursor<queue.size();cursor++){
            Jobs.checkpoint();if(cursor>=262144)throw fault("OC manual navigation exceeds budget");
            String path=queue.get(cursor);if(!visited.add(path))continue;
            JsonObject content=document(manual,path,locale,data,resources);
            data.append(data.documents,object("kind","usageDocument","key",key(path),"content",content));
            for(JsonElement raw:content.getAsJsonArray("links")){
                JsonObject link=raw.getAsJsonObject();if(link.get("kind").getAsString().equals("document")){
                    String target=link.get("target").getAsString();if(wanted.add(target))queue.add(target);
                }
            }
        }
        for(JsonObject resource:resources.values())data.append(data.resources,resource);
        return data;
    }
    private static String key(String path){return Identity.content("usage_doc",object("backend","ocManual","path",path));}
    private static JsonObject document(Object manual,String logical,String locale,Data data,Map<String,JsonObject> resources){
        String requested=logical.replace("%LANGUAGE%",locale);Page page=resolve(manual,requested);
        boolean fallback=false;
        if(page.lines==null&&!locale.equals("en_US")&&logical.contains("%LANGUAGE%")){
            page=resolve(manual,logical.replace("%LANGUAGE%","en_US"));fallback=true;
        }
        JsonArray lines=array(),links=array(),images=array();
        if(page.lines!=null)for(String line:page.lines){
            lines.add(value(line));Matcher matches=LINK.matcher(line);
            while(matches.find()){
                String href=matches.group(3).trim(),label=matches.group(2);
                // Markdown titles follow a quoted suffix; the native parser also uses the first href token.
                int title=href.indexOf(" \"");if(title>=0)href=href.substring(0,title);
                if(matches.group(1).equals("!")){images.add(image(manual,href,label,data,resources));continue;}
                if(href.startsWith("http://")||href.startsWith("https://"))links.add(object("kind","external","label",label,"href",href,"target",href,"anchor",""));
                else {
                    int split=href.indexOf('#');String anchor=split>=0?href.substring(split+1):"",target=split>=0?href.substring(0,split):href;
                    target=target.isEmpty()?logical:relative(target,logical);
                    links.add(object("kind","document","label",label,"href",href,"target",target,"anchor",anchor));
                }
            }
        }
        JsonArray redirects=array();for(String path:page.redirects)redirects.add(value(path));
        return object("kind","manual","path",logical,"resolvedPath",page.path,"requestedLocale",locale,"fallbackAttempted",fallback,
            "status",page.lines==null?"missing":"found","provider",page.provider,"redirects",redirects,"lines",lines,"links",links,"images",images);
    }
    private static Page resolve(Object manual,String path){
        List<String> seen=new ArrayList<>();
        for(int i=0;i<64;i++){
            if(seen.contains(path))throw fault("Native OC manual redirect loop: "+path);
            seen.add(path);Page page=lookup(manual,path);page.redirects.addAll(seen);
            if(page.lines==null||page.lines.isEmpty()||!page.lines.get(0).toLowerCase(Locale.ROOT).startsWith("#redirect "))return page;
            path=relative(page.lines.get(0).substring("#redirect ".length()),path);
        }
        throw fault("Native OC manual redirect budget exceeded");
    }
    private static Page lookup(Object manual,String path){
        Page result=new Page(path);
        for(Object provider:NativeOcQueries.sequence(call(manual,"contentProviders"))){
            NativeUsageAdmission.admit(provider,"content");NativeCellQueries.exact(provider,"li.cil.oc.api.prefab.ResourceContentProvider");
            String domain=(String)field(provider,"resourceDomain"),base=(String)field(provider,"basePath");
            ResourceLocation location=new ResourceLocation(domain,base+(path.startsWith("/")?path.substring(1):path));
            byte[] bytes=resource(location,512*1024);if(bytes==null)continue;
            result.provider=location.toString();result.lines=lines(bytes);return result;
        }
        return result;
    }
    private static JsonObject image(Object manual,String href,String label,Data data,Map<String,JsonObject> resources){
        List<Object> providers=NativeOcQueries.sequence(call(manual,"imageProviders"));
        for(int i=providers.size()-1;i>=0;i--){
            Object tuple=providers.get(i);String kind=imageProvider(tuple,new ArrayList<>()),prefix=(String)call(tuple,"_1");
            if(!href.startsWith(prefix))continue;String content=href.substring(prefix.length());
            if(kind.equals(IMAGE+"TextureImageProvider$")){
                ResourceLocation location=new ResourceLocation(content);byte[] bytes=resource(location,768*1024);
                if(bytes==null)return missing(href,label,"image");
                try{
                    if(bytes.length<8||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71)throw fault("OC manual image is not a native PNG: "+location);
                    if(bytes.length<24)throw fault("Truncated OC manual PNG: "+location);
                    int width=ByteBuffer.wrap(bytes,16,8).getInt(),height=ByteBuffer.wrap(bytes,20,4).getInt();
                    if(width<1||height<1||width>4096||height>4096)throw fault("OC manual PNG dimensions exceed budget");
                    BufferedImage pixels=ImageIO.read(new ByteArrayInputStream(bytes));
                    if(pixels==null||pixels.getWidth()<1||pixels.getHeight()<1||pixels.getWidth()>4096||pixels.getHeight()>4096)throw fault("OC manual PNG dimensions exceed budget");
                    String sha=CanonicalJson.digest(bytes);String key=Identity.content("usage_resource",object("location",location.toString(),"sha256",sha));
                    resources.putIfAbsent(key,object("kind","usageResource","key",key,"location",location.toString(),"mime","image/png",
                        "width",pixels.getWidth(),"height",pixels.getHeight(),"sha256",sha,"bytes",bytes.length,"base64",Base64.getEncoder().encodeToString(bytes)));
                    return object("kind","png","href",href,"label",label,"resource",key);
                }catch(IOException failure){throw new IllegalStateException("Invalid native manual PNG: "+location,failure);}
            }
            JsonArray choices=array();
            if(kind.equals(IMAGE+"OreDictImageProvider$")){
                for(Object member:NativeOreTable.members(content))if(member!=null){
                    if(!(member instanceof ItemStack))throw fault("Invalid native manual ore image member");
                    ItemStack stack=(ItemStack)member;if(stack.getItem()!=null)choices.add(value(data.item(Data.owned(stack))));
                }
            } else {
                int split=content.lastIndexOf('@');String registry=split>0?content.substring(0,split):content;
                int meta=split>0?Integer.parseInt(content.substring(split+1)):0;Item item=null;
                if(kind.equals(IMAGE+"ItemImageProvider$"))item=(Item)Item.itemRegistry.getObject(registry);
                else if(kind.equals(IMAGE+"BlockImageProvider$")){Object block=Block.blockRegistry.getObject(registry);if(block instanceof Block)item=Item.getItemFromBlock((Block)block);}
                else throw fault("Unknown admitted OC image role");
                if(item!=null)choices.add(value(data.item(new ItemStack(item,1,meta))));
            }
            String missingKind=kind.equals(IMAGE+"BlockImageProvider$")?"block":kind.equals(IMAGE+"OreDictImageProvider$")?"oreDict":"item";
            return choices.size()>0?object("kind","items","href",href,"label",label,"items",choices):missing(href,label,missingKind);
        }
        throw fault("No native OC manual image provider: "+href);
    }
    private static JsonObject missing(String href,String label,String reason){
        String warning=reason.equals("image")?"ImageMissing":reason.equals("block")?"BlockMissing":reason.equals("oreDict")?"OreDictMissing":"ItemMissing";
        return object("kind","missing","href",href,"label",label,"nativeWarning","oc:gui.Manual.Warning."+warning);
    }
    static byte[] resource(ResourceLocation location,int limit){
        try(InputStream input=Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(output.size()+count>limit)throw fault("Native OC resource exceeds budget: "+location);output.write(buffer,0,count);}return output.toByteArray();
        }catch(FileNotFoundException missing){return null;}
        catch(IOException failure){throw new IllegalStateException("Native OC resource read failed: "+location,failure);}
    }
    private static List<String> lines(byte[] bytes){
        try{
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            List<String> result=new ArrayList<>();BufferedReader input=new BufferedReader(new StringReader(text));String line;
            while((line=input.readLine())!=null){if(result.size()>=65536||line.length()>65536)throw fault("OC manual text exceeds budget");result.add(line);}return result;
        }catch(IOException failure){throw new IllegalStateException("Invalid native manual UTF-8",failure);}
    }
    static String normalize(String path){
        if(path==null||path.isEmpty()||path.length()>4096||path.indexOf('\0')>=0||path.indexOf('\\')>=0)throw fault("Invalid native manual path");
        String clean=com.google.common.io.Files.simplifyPath(path);if(clean.equals("..")||clean.startsWith("../"))throw fault("Native manual path escapes its root");return clean;
    }
    private static String relative(String path,String base){return normalize(path.startsWith("/")?path:base.substring(0,Math.max(0,base.lastIndexOf('/')+1))+path);}
    private static final class Page {final String path;String provider;List<String> lines;final List<String> redirects=new ArrayList<>();Page(String path){this.path=path;}}
}
