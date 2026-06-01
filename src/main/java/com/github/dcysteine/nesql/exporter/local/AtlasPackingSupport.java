package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class AtlasPackingSupport {

    private AtlasPackingSupport() {}

    static PackLayout computeLayout(List<AtlasSourceImage> sources) {
        int maxWidth = 2048;
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        int usedWidth = 0;
        List<AtlasPlacement> placements = new ArrayList<>();

        for (AtlasSourceImage source : sources) {
            int width = source.image.getWidth();
            int height = source.image.getHeight();

            if (x > 0 && x + width > maxWidth) {
                x = 0;
                y += rowHeight;
                rowHeight = 0;
            }

            placements.add(new AtlasPlacement(source, x, y));
            x += width;
            rowHeight = Math.max(rowHeight, height);
            usedWidth = Math.max(usedWidth, x);
        }

        PackLayout layout = new PackLayout();
        layout.width = nextPowerOfTwo(Math.max(usedWidth, 1));
        layout.height = nextPowerOfTwo(Math.max(y + rowHeight, 1));
        layout.placements = placements;
        return layout;
    }

    static BufferedImage renderAtlas(PackLayout layout) {
        BufferedImage atlasImage = new BufferedImage(layout.width, layout.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlasImage.createGraphics();
        try {
            for (AtlasPlacement placement : layout.placements) {
                graphics.drawImage(placement.source.image, placement.x, placement.y, null);
            }
        } finally {
            graphics.dispose();
        }
        return atlasImage;
    }

    static void writeAtlasImage(File atlasFile, PackLayout layout) throws IOException {
        BufferedImage atlasImage = renderAtlas(layout);
        try {
            ImageIO.write(atlasImage, "PNG", atlasFile);
        } finally {
            atlasImage.flush();
        }
    }

    static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return result;
    }

    static final class AtlasSourceImage {
        final CanonicalRenderAsset asset;
        final File file;
        final BufferedImage image;
        final Integer frameIndex;

        AtlasSourceImage(CanonicalRenderAsset asset, File file, BufferedImage image, Integer frameIndex) {
            this.asset = asset;
            this.file = file;
            this.image = image;
            this.frameIndex = frameIndex;
        }
    }

    static final class AtlasPlacement {
        final AtlasSourceImage source;
        final int x;
        final int y;

        AtlasPlacement(AtlasSourceImage source, int x, int y) {
            this.source = source;
            this.x = x;
            this.y = y;
        }
    }

    static final class PackLayout {
        int width;
        int height;
        List<AtlasPlacement> placements = new ArrayList<>();
    }
}
