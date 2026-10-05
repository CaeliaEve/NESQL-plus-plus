package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;

/** Export-local PNG bytes only; asset provenance/timelines are never cached here.
 * Used by the single encoding worker (oversized images bypass its queue only
 * after pending work drains). Non-default color models retain their encoding.
 */
final class PngCache {
    private final long budget;
    private final int limit;
    private final LinkedHashMap<String, byte[]> cache = new LinkedHashMap<>(16, .75f, true);
    private long bytes;
    PngCache(long budget, int limit) { this.budget = budget; this.limit = limit; }
    byte[] encode(BufferedImage image) throws IOException {
        String key = null;
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            try (Jobs.Timing ignored = Jobs.measure("pngFingerprint")) { key = key(image); }
            byte[] previous = cache.get(key);
            if (previous != null) {
                try (Jobs.Timing ignored = Jobs.measure("pngReuse")) { return previous; }
            }
        }
        byte[] encoded;
        try (Jobs.Timing ignored = Jobs.measure("pngEncode")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder is unavailable");
            encoded = output.toByteArray();
        }
        if (key != null && encoded.length <= budget && limit > 0) {
            while (!cache.isEmpty() && (cache.size() >= limit || bytes + encoded.length > budget)) {
                String oldest = cache.keySet().iterator().next(); bytes -= cache.remove(oldest).length;
            }
            cache.put(key, encoded); bytes += encoded.length;
        }
        return encoded;
    }
    private static String key(BufferedImage image) {
        MessageDigest digest = CanonicalJson.sha256();
        int width = image.getWidth(), height = image.getHeight();
        digest.update(ByteBuffer.allocate(8).putInt(width).putInt(height).array());
        int[] row = new int[width]; ByteBuffer packed = ByteBuffer.allocate(width * 4);
        for (int y = 0; y < height; y++) {
            Jobs.checkpoint(); image.getRGB(0, y, width, 1, row, 0, width); packed.clear();
            for (int value : row) packed.putInt(value);
            digest.update(packed.array());
        }
        return CanonicalJson.hex(digest.digest());
    }
}
