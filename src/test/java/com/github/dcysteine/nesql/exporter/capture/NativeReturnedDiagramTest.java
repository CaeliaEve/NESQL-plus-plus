package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.google.gson.JsonObject;
import java.util.Arrays;

/** Large repeated tool NBT must be referenced by its canonical item fact, not copied per edge. */
final class NativeReturnedDiagramTest {
  static void run() {
    NativeDiagramBindings bindings=new NativeDiagramBindings();
    char[] text=new char[20000];Arrays.fill(text,'x');
    for(int meta=0;meta<1500;meta++) {
      JsonObject nbt=object("type","compound","value",object("toolData",object("type","string","value",new String(text))));
      String id=Identity.item("fixture:material_tool",meta,nbt);
      JsonObject component=object("kind","item","registry","fixture:material_tool","meta",meta,"nbt",nbt,"fact",id);
      for(String direction:new String[]{"crafting","usage"}) {
        JsonObject binding=object("query",direction,"component",component);
        int index=bindings.add(binding,meta);
        if(bindings.index(binding)!=index)throw new AssertionError("Interned query lost its native destination");
        JsonObject encoded=bindings.rows.get(index).getAsJsonObject("component");
        if(!encoded.get("kind").getAsString().equals("itemFact")||!encoded.get("fact").getAsString().equals(id))
          throw new AssertionError("Tool NBT was duplicated into each query edge");
      }
    }
    if(bindings.bytes>=1024*1024||bindings.rows.size()!=3000)
      throw new AssertionError("Repeated tool NBT still exceeds the compact binding budget: "+bindings.bytes);
    System.out.println("Returned diagram: 3000 bindings reference 1500 complete item facts without repeated 20KB NBT");
  }
}
