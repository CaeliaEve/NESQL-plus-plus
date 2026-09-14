package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;

import java.lang.reflect.Proxy;

/** Preview disposal must not run removal callbacks for unticked game machines. */
final class PreviewTest {
    private PreviewTest() {}

    static void run() {
        IGregTechTileEntity[] owner = {null};
        IMetaTileEntity[] machine = {null};
        int[] detached = {0}, removed = {0};
        IGregTechTileEntity base = (IGregTechTileEntity) Proxy.newProxyInstance(PreviewTest.class.getClassLoader(),
                new Class<?>[] {IGregTechTileEntity.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getMetaTileEntity")) return machine[0];
                    if (method.getName().equals("setMetaTileEntity")) { machine[0] = (IMetaTileEntity) arguments[0]; return null; }
                    throw new AssertionError("Unexpected preview base operation: " + method.getName());
                });
        owner[0] = base;
        IMetaTileEntity meta = (IMetaTileEntity) Proxy.newProxyInstance(PreviewTest.class.getClassLoader(),
                new Class<?>[] {IMetaTileEntity.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getBaseMetaTileEntity")) return owner[0];
                    if (method.getName().equals("setBaseMetaTileEntity")) {
                        require(arguments[0] == null, "Disposal attached a different base");
                        detached[0]++; owner[0].setMetaTileEntity(null); owner[0] = null; return null;
                    }
                    if (method.getName().equals("onRemoval")) { removed[0]++; return (new Object[1])[-1]; }
                    throw new AssertionError("Unexpected preview machine operation: " + method.getName());
                });
        machine[0] = meta;
        try { meta.onRemoval(); throw new AssertionError("Expected the uninitialized dam removal failure"); }
        catch (ArrayIndexOutOfBoundsException expected) { require(removed[0] == 1, "Wrong removal reproduction"); }
        removed[0] = 0;
        Preview.detach(base);
        if (base.getMetaTileEntity() != null) base.getMetaTileEntity().onRemoval();
        require(detached[0] == 1 && removed[0] == 0 && owner[0] == null && machine[0] == null,
                "Disposal retained a link or invoked a live-world removal callback");
        Preview.detach(base);
        require(detached[0] == 1, "Repeated disposal detached the machine twice");
        machine[0] = meta;
        try { Preview.detach(base); throw new AssertionError("Accepted a machine owned by another base"); }
        catch (Jobs.Fault expected) { require(expected.getMessage().contains("different tile"), "Missing ownership failure"); }
        require(machine[0] == meta && detached[0] == 1, "Ownership rejection changed the machine");

        int[] invalidations = {0};
        TileEntity tile = new TileEntity() { @Override public void invalidate() { invalidations[0]++; super.invalidate(); } };
        try (Preview.Space world = new Preview.Space(); Preview.Space foreign = new Preview.Space()) {
            world.setBlock(0, 64, 0, Blocks.stone, 0, 2);
            world.setTileEntity(0, 64, 0, tile);
            world.removeTileEntity(0, 64, 0);
            require(invalidations[0] == 1 && tile.isInvalid() && world.getTileEntity(0, 64, 0) == null,
                    "Ordinary preview tile removal lost invalidation");
            world.setTileEntity(0, 64, 0, tile);
            world.setTileEntity(0, 64, 0, new TileEntity());
            require(invalidations[0] == 2, "Replacing a preview tile did not retire its predecessor");
            TileEntity outsider = new TileEntity(); outsider.setWorldObj(foreign);
            try { world.setTileEntity(0, 64, 0, outsider); throw new AssertionError("Adopted a foreign tile"); }
            catch (Jobs.Fault expected) { require(outsider.getWorldObj() == foreign && !outsider.isInvalid(), "Foreign tile was modified"); }
        }
        int[] attempted = {0};
        Preview.Space failing = new Preview.Space();
        for (int x = 0; x < 2; x++) {
            failing.setBlock(x, 64, 0, Blocks.stone, 0, 2);
            failing.setTileEntity(x, 64, 0, new TileEntity() {
                @Override public void invalidate() { attempted[0]++; throw new IllegalStateException("fixture release"); }
            });
        }
        try { failing.close(); throw new AssertionError("Release failures were hidden"); }
        catch (Jobs.Fault expected) {
            require(attempted[0] == 2 && expected.getSuppressed().length == 1 && failing.blocks.isEmpty()
                    && failing.getTileEntity(0, 64, 0) == null, "Disposal stopped early or retained the preview maps");
        }
        failing.close();
        require(attempted[0] == 2, "Closing a failed preview retried removal hooks");
        System.out.println("Preview disposal: unticked machine detachment, no removal hook, ordinary invalidation, foreign ownership and failure cleanup passed");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
