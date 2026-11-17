package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.BufferUploads;
import com.github.dcysteine.nesql.exporter.core.ModelDisplayLists;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.model.ModelRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.VisualValues.*;

/** Angelica's negative display-list handles refer to real GTNHLib VBOs.
 * Read their uploaded geometry instead of treating a negative handle as a
 * missing GL list. A native upload/close epoch invalidates the small CPU cache. */
final class NativeModelBuffers implements AutoCloseable {
    private final IdentityHashMap<Object,Cached> cache=new IdentityHashMap<>();
    JsonObject part(ModelRenderer part,JsonObject projected){
        ModelDisplayLists.Authority proof=ModelDisplayLists.bind(part,0.0625F);int handle=proof.handle();
        NativeAuthorities.current().remember(part,"model",proof::verify);
        if(handle>0){if(!GL11.glIsList(handle))throw fault("Native inventory display list is not live");
            NativeAuthorities.current().remember(part,"displayList",()->{proof.verify();if(!GL11.glIsList(handle))throw fault("Native inventory display list was deleted");});return projected;}
        MagicApi.version("NativeModelBuffers","angelica");MagicApi.version("NativeModelBuffers","gtnhlib");
        Class<?> manager=MagicApi.type("com.gtnewhorizon.gtnhlib.client.renderer.vbo.VBOManager");Object table=MagicApi.field(manager,null,"vertexBuffers");
        if(!table.getClass().getName().equals("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap"))throw fault("Native model VBO registry owner changed");
        Object buffer=MagicApi.invoke(table.getClass(),table,"get",new Class<?>[]{int.class},handle-Integer.MIN_VALUE);
        if(buffer==null||!buffer.getClass().getName().equals("com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer"))throw fault("Native model VBO is missing");
        int id=((Number)MagicApi.field(buffer,"id")).intValue(),count=((Number)MagicApi.field(buffer,"vertexCount")).intValue();
        Object format=MagicApi.field(buffer,"format"),epoch=BufferUploads.revision(buffer);
        if(id<1||count<4||count%4!=0||count>16384||((Number)MagicApi.field(buffer,"drawMode")).intValue()!=GL11.GL_QUADS)
            throw fault("Native model VBO has an invalid draw census");
        format(format);
        NativeAuthorities.current().remember(buffer,"vbo",()->{
            if(MagicApi.field(manager,null,"vertexBuffers")!=table||MagicApi.invoke(table.getClass(),table,"get",new Class<?>[]{int.class},handle-Integer.MIN_VALUE)!=buffer
                    ||BufferUploads.revision(buffer)!=epoch||((Number)MagicApi.field(buffer,"id")).intValue()!=id||((Number)MagicApi.field(buffer,"vertexCount")).intValue()!=count
                    ||MagicApi.field(buffer,"format")!=format||((Number)MagicApi.field(buffer,"drawMode")).intValue()!=GL11.GL_QUADS||!GL15.glIsBuffer(id))
                throw fault("Native model VBO source/upload authority changed");format(format);
        });
        Cached known=cache.get(buffer);
        if(known==null||known.epoch!=epoch||known.id!=id||known.count!=count||known.format!=format){
            int previous=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);JsonArray vertices;
            try{
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,id);int length=Math.multiplyExact(count,24);
                if(!GL15.glIsBuffer(id)||GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER,GL15.GL_BUFFER_SIZE)!=length)throw fault("Native model VBO byte size changed");
                ByteBuffer bytes=BufferUtils.createByteBuffer(length).order(ByteOrder.nativeOrder());GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER,0,bytes);
                vertices=vertices(bytes,count);
                if(BufferUploads.revision(buffer)!=epoch)throw fault("Native model VBO was uploaded while reading");
            }finally{GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,previous);}
            known=new Cached(epoch,format,id,count,vertices);cache.put(buffer,known);
        }
        projected.add("vertices",known.vertices);proof.verify();return projected;
    }
    private static void format(Object format){
        Class<?> defaults=MagicApi.type("com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat");
        if(format!=MagicApi.field(defaults,null,"POSITION_TEXTURE_NORMAL")||!format.getClass().getName().equals("com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat")
                ||((Number)MagicApi.field(format,"vertexSize")).intValue()!=24
                ||!MagicApi.field(format,"quadWriter").getClass().getName().equals("com.gtnewhorizon.gtnhlib.client.renderer.quad.writers.ItemVBOQuadWriter"))
            throw fault("Unreviewed native model VBO format");
        List<?> elements=(List<?>)MagicApi.field(format,"elements");String[] names={"POSITION_ELEMENT","TEXTURE_ELEMENT","NORMAL_ELEMENT","PADDING_ELEMENT"};
        if(elements.size()!=names.length)throw fault("Native model VBO element census changed");
        int[] counts={3,2,3,1},sizes={12,8,3,1},offsets={0,12,20,23};String[] types={"FLOAT","FLOAT","BYTE","BYTE"},uses={"POSITION","UV","NORMAL","PADDING"};
        Object nativeOffsets=MagicApi.field(format,"offsets");
        if(!nativeOffsets.getClass().getName().equals("it.unimi.dsi.fastutil.ints.IntArrayList"))throw fault("Native model VBO offset owner changed");
        for(int i=0;i<names.length;i++){
            Object element=elements.get(i);
            if(element!=MagicApi.field(defaults,null,names[i])||!element.getClass().getName().equals("com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement")
                    ||((Number)MagicApi.field(element,"index")).intValue()!=0||((Number)MagicApi.field(element,"count")).intValue()!=counts[i]
                    ||((Number)MagicApi.field(element,"byteSize")).intValue()!=sizes[i]||!((Enum<?>)MagicApi.field(element,"type")).name().equals(types[i])
                    ||!((Enum<?>)MagicApi.field(element,"usage")).name().equals(uses[i])
                    ||((Number)MagicApi.invoke(nativeOffsets.getClass(),nativeOffsets,"getInt",new Class<?>[]{int.class},i)).intValue()!=offsets[i])
                throw fault("Native model VBO element/offset layout changed");
        }
        for(String field:new String[]{"setupBufferStateOverrride","clearBufferStateOverrride"})
            if(!((List<?>)MagicApi.field(format.getClass(),null,field)).isEmpty())throw fault("Native model VBO state callback requires a dedicated adapter");
    }
    /** Float positions/UV and signed byte normals are the uploaded native
     * POSITION_TEXTURE_NORMAL format, not Angelica's Tessellator rawBuffer. */
    static JsonArray vertices(ByteBuffer bytes,int count){
        if(count<4||count%4!=0||count>16384||bytes.remaining()!=count*24)throw fault("Native model VBO is not ordered quads");
        JsonArray result=new JsonArray();int start=bytes.position();
        for(int quad=0;quad<count;quad+=4)for(int vertex:new int[]{0,1,2,0,2,3}){
            int at=start+(quad+vertex)*24;
            result.add(floats(bytes.getFloat(at),bytes.getFloat(at+4),bytes.getFloat(at+8),bytes.get(at+20)/127F,bytes.get(at+21)/127F,bytes.get(at+22)/127F,
                    bytes.getFloat(at+12),bytes.getFloat(at+16)));
        }return result;
    }
    private static final class Cached {
        final Object epoch,format;final int id,count;final JsonArray vertices;
        Cached(Object epoch,Object format,int id,int count,JsonArray vertices){this.epoch=epoch;this.format=format;this.id=id;this.count=count;this.vertices=vertices;}
    }
    @Override public void close(){cache.clear();}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("animation_unsupported",message);}
}
