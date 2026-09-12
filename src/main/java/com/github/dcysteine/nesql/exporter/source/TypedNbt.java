package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.*;

import java.util.Base64;
import java.util.Locale;

/** Lossless NBT: integer types, IEEE bit patterns, array types and list element types survive JSON. */
public final class TypedNbt {
    private static final String[] TYPES = {
            "end", "byte", "short", "int", "long", "float", "double", "byte_array", "string", "list", "compound", "int_array"
    };
    private int nodes;

    private TypedNbt() {}

    public static JsonElement encode(NBTBase tag) {
        if (tag == null) return JsonNull.INSTANCE;
        JsonElement value = new TypedNbt().node(tag, 0);
        if (CanonicalJson.bytes(value).length > Dataset.RECORD_LIMIT / 2) {
            throw new IllegalArgumentException("NBT exceeds 512 KiB");
        }
        return value;
    }

    private JsonObject node(NBTBase tag, int depth) {
        if (depth > 48 || ++nodes > 65536) throw new IllegalArgumentException("NBT exceeds nesting or node limit");
        int type = tag.getId();
        if (type < 1 || type >= TYPES.length) throw new IllegalArgumentException("Unsupported NBT type: " + type);
        JsonObject result = new JsonObject();
        result.addProperty("type", TYPES[type]);
        switch (type) {
            case 1: result.addProperty("value", Byte.toString(((NBTTagByte) tag).func_150290_f())); break;
            case 2: result.addProperty("value", Short.toString(((NBTTagShort) tag).func_150289_e())); break;
            case 3: result.addProperty("value", Integer.toString(((NBTTagInt) tag).func_150287_d())); break;
            case 4: result.addProperty("value", Long.toString(((NBTTagLong) tag).func_150291_c())); break;
            case 5: result.addProperty("value", String.format(Locale.ROOT, "%08x", Float.floatToRawIntBits(((NBTTagFloat) tag).func_150288_h()))); break;
            case 6: result.addProperty("value", String.format(Locale.ROOT, "%016x", Double.doubleToRawLongBits(((NBTTagDouble) tag).func_150286_g()))); break;
            case 7: {
                byte[] value = ((NBTTagByteArray) tag).func_150292_c();
                if (value.length > 256 * 1024) throw new IllegalArgumentException("NBT byte array exceeds size limit");
                result.addProperty("value", Base64.getEncoder().encodeToString(value));
                break;
            }
            case 8: {
                String value = ((NBTTagString) tag).func_150285_a_();
                if (value.length() > 131072) throw new IllegalArgumentException("NBT string exceeds size limit");
                result.addProperty("value", value);
                break;
            }
            case 9: {
                NBTTagList list = (NBTTagList) tag;
                int element = list.func_150303_d();
                if (element < 0 || element >= TYPES.length) throw new IllegalArgumentException("Unsupported NBT list element type");
                result.addProperty("element", TYPES[element]);
                JsonArray values = new JsonArray();
                if (list.tagCount() > 65536) throw new IllegalArgumentException("NBT list exceeds node limit");
                NBTTagList copy = (NBTTagList) list.copy();
                JsonElement[] elements = new JsonElement[copy.tagCount()];
                // 1.7.10 has no generic indexed getter. Removing from a copy's tail is linear,
                // supports every tag type and never mutates the game's original list.
                for (int index = elements.length - 1; index >= 0; index--) elements[index] = node(copy.removeTag(index), depth + 1);
                for (JsonElement value : elements) values.add(value);
                result.add("value", values);
                break;
            }
            case 10: {
                NBTTagCompound compound = (NBTTagCompound) tag;
                JsonObject values = new JsonObject();
                for (Object key : compound.func_150296_c()) values.add((String) key, node(compound.getTag((String) key), depth + 1));
                result.add("value", values);
                break;
            }
            case 11: {
                int[] array = ((NBTTagIntArray) tag).func_150302_c();
                if (array.length > 65536) throw new IllegalArgumentException("NBT int array exceeds size limit");
                JsonArray values = new JsonArray();
                for (int value : array) values.add(new JsonPrimitive(Integer.toString(value)));
                result.add("value", values);
                break;
            }
            default: throw new AssertionError(type);
        }
        return result;
    }
}
