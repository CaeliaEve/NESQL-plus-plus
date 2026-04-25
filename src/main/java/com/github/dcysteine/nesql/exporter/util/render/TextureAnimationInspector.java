package com.github.dcysteine.nesql.exporter.util.render;

import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.IIcon;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class TextureAnimationInspector {
    private static final Class<?> PATCHED_SPRITE_CLASS = resolvePatchedSpriteClass();

    private TextureAnimationInspector() {}

    static boolean isAnimatedIcon(Object icon) {
        if (icon == null) {
            return false;
        }

        if (icon instanceof TextureAtlasSprite) {
            TextureAtlasSprite sprite = (TextureAtlasSprite) icon;
            try {
                return sprite.hasAnimationMetadata() || sprite.getFrameCount() > 1;
            } catch (Exception ignored) {
                return sprite.hasAnimationMetadata();
            }
        }

        return PATCHED_SPRITE_CLASS != null && PATCHED_SPRITE_CLASS.isInstance(icon);
    }

    static boolean containsAnimatedIcon(Object root) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        return containsAnimatedIcon(root, visited);
    }

    private static boolean containsAnimatedIcon(Object root, Set<Object> visited) {
        if (root == null) {
            return false;
        }

        if (isAnimatedIcon(root)) {
            return true;
        }

        if (root instanceof IIconContainer) {
            IIconContainer iconContainer = (IIconContainer) root;
            try {
                IIcon icon = iconContainer.getIcon();
                if (isAnimatedIcon(icon)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            try {
                IIcon overlayIcon = iconContainer.getOverlayIcon();
                if (isAnimatedIcon(overlayIcon)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        Class<?> rootClass = root.getClass();
        if (rootClass.isArray()) {
            int length = Array.getLength(root);
            for (int i = 0; i < length; i++) {
                if (containsAnimatedIcon(Array.get(root, i), visited)) {
                    return true;
                }
            }
            return false;
        }

        if (root instanceof Iterable<?>) {
            for (Object value : (Iterable<?>) root) {
                if (containsAnimatedIcon(value, visited)) {
                    return true;
                }
            }
            return false;
        }

        if (!shouldReflectInto(root)) {
            return false;
        }

        if (!visited.add(root)) {
            return false;
        }

        Class<?> type = rootClass;
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (fieldType.isPrimitive() || fieldType.isEnum() || fieldType == String.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(root);
                    if (containsAnimatedIcon(value, visited)) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
            type = type.getSuperclass();
        }

        return false;
    }

    private static boolean shouldReflectInto(Object value) {
        return value instanceof ITexture
                || value instanceof IIconContainer
                || value instanceof IIcon
                || value.getClass().getName().startsWith("gregtech.")
                || value.getClass().getName().startsWith("net.minecraft.client.renderer.texture.");
    }

    private static Class<?> resolvePatchedSpriteClass() {
        try {
            return Class.forName("com.mitchej123.hodgepodge.textures.IPatchedTextureAtlasSprite");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
