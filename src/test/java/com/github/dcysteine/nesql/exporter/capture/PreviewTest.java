package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;

import java.lang.reflect.Proxy;

/** Preview disposal must not run removal callbacks for unticked game machines. */
final class PreviewTest {
    private PreviewTest() {}

    static void run() {
        states();
        progress();
        alternatives();
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

    private static void states() {
        try (Preview.Space world = new Preview.Space()) {
            for (int meta : new int[] {0, 15, 16, 305, 32768, 65535}) {
                world.setBlock(2, 64, 3, Blocks.stone, meta, 2);
                require(world.getBlockMetadata(2, 64, 3) == meta, "Preview truncated extended block metadata");
                require(!world.setBlock(2, 64, 3, Blocks.stone, meta, 2), "Identical block placement changed the preview");
            }
            world.setTileEntity(2, 64, 3, new TileEntity());
            world.setBlockMetadataWithNotify(2, 64, 3, 4096, 2);
            require(world.getBlockMetadata(2, 64, 3) == 4096 && world.getTileEntity(2, 64, 3).blockMetadata == 4096,
                    "Extended metadata update did not reach the tile");
            for (int meta : new int[] {-1, 65536}) {
                try { world.setBlock(2, 64, 3, Blocks.stone, meta, 2); throw new AssertionError("Accepted out-of-range metadata"); }
                catch (Jobs.Fault expected) { require(expected.getMessage().contains(Integer.toString(meta)), "Missing invalid state context"); }
                try { world.setBlockMetadataWithNotify(2, 64, 3, meta, 2); throw new AssertionError("Accepted out-of-range metadata update"); }
                catch (Jobs.Fault expected) { require(world.getBlockMetadata(2, 64, 3) == 4096, "Rejected metadata changed the world"); }
            }
        }
    }

    private static void progress() {
        Preview.Progress progress = new Preview.Progress();
        for (int call = 0; call < 7; call++) require(progress.pending(1, 0, 0), "Stopped before the stall budget");
        require(progress.pending(1, 0, 1), "A real placement did not reset the stall budget");
        for (int call = 0; call < 7; call++) require(progress.pending(1, 1, 1), "Stopped after progress too early");
        try { progress.pending(1, 1, 1); throw new AssertionError("An unchanged positive result can loop indefinitely"); }
        catch (Jobs.Fault expected) { require(expected.getMessage().contains("without changing"), "Missing stalled construction reason"); }
        require(!new Preview.Progress().pending(-1, 0, 1), "Native completion was ignored");
        require(!new Preview.Progress().pending(0, 1, 1), "Native exhaustion was ignored");
    }

    private static void alternatives() {
        // The native chain continues after SKIP. A controlled liquid placer
        // reproduces BBF's air/liquid cycle without a player or live inventory.
        IStructureElement<Object> liquid = new IStructureElement<Object>() {
            @Override public boolean check(Object context, World world, int x, int y, int z) { return world.getBlock(x, y, z) == Blocks.lava; }
            @Override public boolean spawnHint(Object context, World world, int x, int y, int z, ItemStack trigger) { return false; }
            @Override public boolean placeBlock(Object context, World world, int x, int y, int z, ItemStack trigger) {
                world.setBlock(x, y, z, Blocks.lava, 1, 2); return true;
            }
            @Override public PlaceResult survivalPlaceBlock(Object context, World world, int x, int y, int z, ItemStack trigger, AutoPlaceEnvironment env) {
                if (check(context, world, x, y, z)) return PlaceResult.SKIP;
                placeBlock(context, world, x, y, z, trigger); return PlaceResult.ACCEPT;
            }
        };
        IStructureElement<Object> chain = StructureUtility.ofChain(StructureUtility.isAir(), liquid);
        AutoPlaceEnvironment env = AutoPlaceEnvironment.fromLegacy(null, null, message -> {});
        try (Preview.Space world = new Preview.Space()) {
            for (int call = 0; call < 4; call++) {
                long before = world.changes;
                require(chain.survivalPlaceBlock(null, world, 0, 64, 0, null, env) == IStructureElement.PlaceResult.ACCEPT
                        && world.changes > before && chain.check(null, world, 0, 64, 0), "Native alternative-chain oscillation changed");
            }
            chain.placeBlock(null, world, 0, 64, 0, null);
            require(chain.check(null, world, 0, 64, 0), "Native creative placement did not satisfy the alternative");
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
