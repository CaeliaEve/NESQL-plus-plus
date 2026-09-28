package com.github.dcysteine.nesql.exporter.task;

final class EpochTest {
    static void run() {
        Epoch epoch = new Epoch();
        Object world = new Object(), player = new Object();
        String first = epoch.observe(world, player);
        require(first.matches("[a-f0-9]{64}") && first.equals(epoch.observe(world, player)), "Unchanged session is unstable");
        require(!first.equals(new Epoch().observe(world, player)), "Reused a previous game process");
        epoch.observe(null, null);
        String returned = epoch.observe(world, player);
        require(!first.equals(returned), "Returning to a world reused a closed session");
        String respawn = epoch.observe(world, new Object());
        require(!returned.equals(respawn), "Player replacement reused a session");
        epoch.reload();
        require(!respawn.equals(epoch.observe(world, player)), "Resource reload reused a session");
        String before = epoch.observe(world, player);
        epoch.reload();
        require(!before.equals(epoch.observe(world, player)), "Same-world resource reload was ignored");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
