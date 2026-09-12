package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Access only the explicitly named API members audited for the pinned magic mods. */
final class MagicApi {
    private MagicApi() {}
    static void version(String id, String expected) {
        ModContainer mod = Loader.instance().getIndexedModList().get(id);
        if (mod == null || !expected.equals(mod.getVersion())) throw fault("Magic recipes require " + id + " " + expected);
    }
    static Class<?> type(String name) {
        try { return Class.forName(name); } catch (ClassNotFoundException error) { throw fault("Required magic API is unavailable: " + name); }
    }
    static Object field(Object target, String name) { return field(target.getClass(), target, name); }
    static Object field(Class<?> owner, Object target, String name) {
        for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException ignored) { }
            catch (ReflectiveOperationException error) { throw fault(error.toString()); }
        }
        throw fault("Missing pinned field " + owner.getName() + "." + name);
    }
    static Object invoke(Class<?> owner, Object target, String name, Class<?>[] parameters, Object... arguments) {
        for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
            try { Method method = current.getDeclaredMethod(name, parameters); method.setAccessible(true); return method.invoke(target, arguments); }
            catch (NoSuchMethodException ignored) { }
            catch (ReflectiveOperationException error) { throw fault("Pinned magic API call failed: " + owner.getName() + "." + name + ": " + error); }
        }
        throw fault("Missing pinned method " + owner.getName() + "." + name);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("magic_recipe", message); }
}
