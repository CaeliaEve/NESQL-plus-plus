package com.github.dcysteine.nesql.exporter.core;

import java.util.*;

/** Native VertexBuffer upload/close epochs. Tokens cannot be reconstructed from
 * a buffer number or JSON, and weak keys do not retain dead game buffers. */
public final class BufferUploads {
    private static final Map<Object,Token> revisions=Collections.synchronizedMap(new WeakHashMap<>());
    private BufferUploads(){}
    public static void changed(Object buffer){
        if(buffer==null||!buffer.getClass().getName().equals("com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer"))return;
        try{if(buffer.getClass().getMethod("hashCode").getDeclaringClass()!=Object.class)return;}catch(NoSuchMethodException unavailable){return;}
        revisions.put(buffer,new Token());
    }
    public static Object revision(Object buffer){Token token=revisions.get(buffer);if(token==null||token.thread!=Thread.currentThread())throw new IllegalStateException("Native model VBO has no observed upload epoch");return token;}
    private static final class Token {final Thread thread=Thread.currentThread();}
}
