package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonObject;
import com.github.dcysteine.nesql.exporter.task.CaptureOutcome;
import com.github.dcysteine.nesql.exporter.task.CaptureAccounting;
import com.github.dcysteine.nesql.exporter.core.RendererRandom;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real files and the normal Rows/Dataset writer; no Minecraft or image timing mocks. */
final class CheckpointsTest {
    static void run(Path root) throws Exception {
        continuations(root.resolve("capabilities"));
        semantic();
        Files.createDirectories(root);
        List<String> handlers = Arrays.asList("category_" + repeat('a'), "category_" + repeat('b'));
        JsonObject proof = object("revision", 1, "environment", repeat('1'), "runtime", repeat('2'),
                "selection", repeat('3'), "session", repeat('4'));
        JsonObject environment = object("fixture", "checkpoint");
        nativeReplayInterrupted(root.resolve("native-interrupted"), proof, handlers, environment);
        byte[] pixels = {1, 2, 3, 4};
        String image = "assets/" + CanonicalJson.digest(pixels) + ".png";
        JsonObject item = object("id", "item_" + repeat('5'), "order", 7);
        JsonObject recipe = object("id", "recipe_" + repeat('6'), "category", handlers.get(0));
        CaptureAccounting accounting = new CaptureAccounting();
        CaptureOutcome captured = CaptureOutcome.recipes(1, array()), excluded = CaptureOutcome.excluded(object("reason", "native test predicate is empty"));
        accounting.append(0, captured); accounting.append(1, excluded);
        JsonObject[] saved = {null};
        Path first = root.resolve("first");
        try (Checkpoints checkpoint = new Checkpoints(first, proof, handlers, "fixture", value -> saved[0] = value)) {
            checkpoint.begin("base");
            Path original = root.resolve("image.png"); Files.write(original, pixels);
            checkpoint.asset(original, image);
            checkpoint.row("items", item);
            checkpoint.finish(object());
            checkpoint.begin(handlers.get(0));
            checkpoint.row("recipes", recipe);
            checkpoint.outcome(0, captured); checkpoint.outcome(1, excluded);
            checkpoint.finish(object("exclusions", object("1", excluded.json().get("proof")), "accounting", accounting.json()));
            require(checkpoint.accounting().getAsJsonObject(handlers.get(0)).equals(accounting.json()), "Sealed native accounting was lost");
            checkpoint.begin(handlers.get(1));
            checkpoint.row("recipes", object("id", "partial"));
            // A later native failure leaves an unfinished unit; it is not in the receipt.
            checkpoint.release(true);
        }
        String digest = saved[0].get("sha256").getAsString();
        Path manifestPath = first.resolve("manifests").resolve(digest + ".json");
        JsonObject manifest = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(manifestPath),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        require(manifest.has("sourceRevision") && manifest.get("sourceRevision").getAsInt() == Dataset.REVISION,
                "Checkpoint does not bind its Source contract");
        require(manifest.has("presentation") && manifest.get("presentation").getAsString().equals("semantic-content-v1"),
                "Checkpoint does not bind its capture presentation");
        JsonObject oldAccounting = new com.google.gson.JsonParser().parse(manifest.toString()).getAsJsonObject();
        oldAccounting.addProperty("semanticRevision", 2);
        String oldAccountingHash = CanonicalJson.digest(oldAccounting);
        Files.write(first.resolve("manifests").resolve(oldAccountingHash + ".json"), CanonicalJson.bytes(oldAccounting));
        Files.write(first.resolve("released.json"), CanonicalJson.bytes(object("sha256", oldAccountingHash)));
        reject(root.resolve("old-accounting"), first, oldAccountingHash, proof, handlers, environment,
                "Accepted a prefix without the current native disposition protocol");
        Files.write(first.resolve("released.json"), CanonicalJson.bytes(object("sha256", digest)));
        JsonObject otherPresentation = new com.google.gson.JsonParser().parse(manifest.toString()).getAsJsonObject();
        otherPresentation.addProperty("presentation", "native-gui");
        String otherHash = CanonicalJson.digest(otherPresentation);
        Files.write(first.resolve("manifests").resolve(otherHash + ".json"), CanonicalJson.bytes(otherPresentation));
        Files.write(first.resolve("released.json"), CanonicalJson.bytes(object("sha256", otherHash)));
        reject(root.resolve("changed-presentation"), first, otherHash, proof, handlers, environment,
                "Accepted a different capture capability even with a valid receipt hash");
        Files.write(first.resolve("released.json"), CanonicalJson.bytes(object("sha256", digest)));
        String resumed;
        try (Dataset dataset = new Dataset(root.resolve("resumed"), "fixture", environment, false);
             Rows rows = new Rows(root.resolve("resumed-sort"));
             Checkpoints checkpoint = new Checkpoints(root.resolve("second"), proof, handlers, "fixture", value -> {})) {
            int[] restored = {0};
            int count = checkpoint.replay(first, digest, dataset, rows, (kind, row) -> restored[0]++, (unit,evidence) -> {});
            require(count == 2 && restored[0] == 2, "Replayed a partial unit or lost completed records");
            require(checkpoint.accounting().getAsJsonObject(handlers.get(0)).equals(accounting.json()), "Replay lost native accounting");
            checkpoint.begin(handlers.get(1));
            JsonObject last = object("id", "recipe_last", "category", handlers.get(1));
            rows.add("recipes", last); checkpoint.row("recipes", last); checkpoint.finish(object());
            rows.write(dataset); resumed = dataset.seal();
        }
        try (Dataset dataset = new Dataset(root.resolve("direct"), "fixture", environment, false);
             Rows rows = new Rows(root.resolve("direct-sort"))) {
            dataset.asset(pixels, "png"); rows.add("items", item); rows.add("recipes", recipe);
            rows.add("recipes", object("id", "recipe_last", "category", handlers.get(1)));
            rows.write(dataset);
            require(resumed.equals(dataset.seal()), "Resume changed the ordinary Source byte identity");
        }
        JsonObject changed = new com.google.gson.JsonParser().parse(proof.toString()).getAsJsonObject();
        changed.addProperty("session", repeat('9'));
        reject(root.resolve("stale"), first, digest, changed, handlers, environment, "Accepted another game session");
        reject(root.resolve("wrong-hash"), first, repeat('0'), proof, handlers, environment, "Accepted an unpinned receipt");
        Path rejected = root.resolve("changed-native");
        try (Dataset dataset = new Dataset(root.resolve("changed-work"), "fixture", environment, false);
             Rows rows = new Rows(root.resolve("changed-sort"));
             Checkpoints checkpoint = new Checkpoints(rejected, proof, handlers, "fixture", value -> saved[0] = value)) {
            try {
                checkpoint.replay(first, digest, dataset, rows, (kind, row) -> {}, (index, evidence) -> {
                    if (index == 1) throw new java.io.IOException("native facts changed");
                });
                throw new AssertionError("Native validation failure was ignored");
            } catch (java.io.IOException expected) { require(expected.getMessage().equals("native facts changed"), "Wrong native validation failure"); }
            checkpoint.release(true);
        }
        require(saved[0].get("units").getAsInt() == 1 && !Files.exists(root.resolve("changed-work")),
                "A semantically changed unit was sealed or published");
        try (Checkpoints checkpoint = new Checkpoints(root.resolve("false-accounting"), proof, handlers, "fixture", value -> {})) {
            checkpoint.begin("base"); checkpoint.finish(object()); checkpoint.begin(handlers.get(0));
            checkpoint.row("recipes", recipe); checkpoint.outcome(0, captured);
            try { checkpoint.finish(object("accounting", accounting.json())); throw new AssertionError("Missing native outcome was sealed"); }
            catch (java.io.IOException expected) { require(expected.getMessage().contains("ledger"), "Wrong accounting rejection"); }
            checkpoint.release(true);
        }
        byte[] original = Files.readAllBytes(first.resolve("units/unit-0001.jsonl"));
        Files.write(first.resolve("units/unit-0001.jsonl"), new byte[]{'{', '}'});
        reject(root.resolve("corrupt"), first, digest, proof, handlers, environment, "Accepted corrupt unit data");
        Files.write(first.resolve("units/unit-0001.jsonl"), original);
        Files.write(first.resolve("blobs").resolve(CanonicalJson.digest(pixels) + ".png"), new byte[]{8});
        reject(root.resolve("corrupt-asset"), first, digest, proof, handlers, environment, "Accepted corrupt asset bytes");
        Path unsafe = root.resolve("unsafe");
        try (Checkpoints checkpoint = new Checkpoints(unsafe, proof, handlers, "fixture", value -> saved[0] = value)) {
            checkpoint.begin("base"); checkpoint.finish(object()); checkpoint.release(false);
        }
        reject(root.resolve("unclean"), unsafe, saved[0].get("sha256").getAsString(), proof, handlers, environment,
                "Accepted a checkpoint after failed native cleanup");
        Path visual = root.resolve("native-program");
        try (Checkpoints checkpoint = new Checkpoints(visual, proof, handlers, "fixture", value -> saved[0] = value)) {
            checkpoint.begin("base");checkpoint.row("visuals",object("id","visual_owned","data",object("kind","javaRandom")));
            checkpoint.finish(object());checkpoint.release(true);
        }
        String nativeDigest=saved[0].get("sha256").getAsString();
        try(Continuations<String> states=new Continuations<>(2)){
            states.begin(visual);states.save(visual,nativeDigest,1,"native-boundary");states.release(visual,true);
            Checkpoints.NativeReplay nativeProof=new Checkpoints.NativeReplay(){
                private Continuations.Lease<String> lease;
                @Override public void verify(Path previous,String digest,int units,boolean required)throws Exception{
                    if(!required)throw new AssertionError("The native classification was lost");lease=states.acquire(previous,digest,units);
                }
                @Override public void sealed(int unit)throws Exception{require(lease.state(unit).equals("native-boundary"),"Wrong native unit state");}
            };
            String nativeResumed;
            try(Dataset dataset=new Dataset(root.resolve("native-capable-work"),"fixture",environment,false);
                Rows rows=new Rows(root.resolve("native-capable-sort"));
                Checkpoints checkpoint=new Checkpoints(root.resolve("native-capable-replay"),proof,handlers,"fixture",value->{})){
                require(checkpoint.replay(visual,nativeDigest,dataset,rows,(kind,row)->{},(unit,evidence)->{},nativeProof)==1,"A native-attested prefix was not replayed");
                rows.write(dataset);nativeResumed=dataset.seal();checkpoint.release(true);
            }
            try(Dataset dataset=new Dataset(root.resolve("native-capable-direct"),"fixture",environment,false);Rows rows=new Rows(root.resolve("native-capable-direct-sort"))){
                rows.add("visuals",object("id","visual_owned","data",object("kind","javaRandom")));rows.write(dataset);
                require(nativeResumed.equals(dataset.seal()),"Native-attested replay changed Source bytes");
            }
        }
        try (Dataset dataset = new Dataset(root.resolve("native-program-work"),"fixture",environment,false);
             Rows rows = new Rows(root.resolve("native-program-sort"));
             Checkpoints checkpoint = new Checkpoints(root.resolve("native-program-replay"),proof,handlers,"fixture",value -> {})) {
            try { checkpoint.replay(visual,saved[0].get("sha256").getAsString(),dataset,rows,
                    (kind,row) -> {throw new AssertionError("Native program was replayed before its state gate");},(unit,evidence) -> {});
                throw new AssertionError("Visual JSON was accepted as renderer/Random recovery authority");
            } catch (java.io.IOException expected) { require(expected.getMessage().contains("renderer/Random"),"Wrong native visual recovery rejection"); }
        }
        Path visualManifest=visual.resolve("manifests").resolve(saved[0].get("sha256").getAsString()+".json");
        JsonObject relabelled=new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(visualManifest),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        relabelled.getAsJsonArray("units").get(0).getAsJsonObject().addProperty("nativeVisuals",false);
        String relabelledHash=CanonicalJson.digest(relabelled);
        Files.write(visual.resolve("manifests").resolve(relabelledHash+".json"),CanonicalJson.bytes(relabelled));
        Files.write(visual.resolve("released.json"),CanonicalJson.bytes(object("sha256",relabelledHash)));
        reject(root.resolve("native-program-relabelled"),visual,relabelledHash,proof,handlers,environment,"A relabelled native program bypassed its recovery gate");
        System.out.println("Handler checkpoints: completed prefix, exact replay, stale/corrupt/unclean rejection passed");
    }

    private static void reject(Path root, Path previous, String digest, JsonObject proof, List<String> handlers,
                               JsonObject environment, String message) throws Exception {
        Files.createDirectories(root);
        try (Dataset dataset = new Dataset(root.resolve("work"), "fixture", environment, false);
             Rows rows = new Rows(root.resolve("sort"));
             Checkpoints checkpoint = new Checkpoints(root.resolve("checkpoints"), proof, handlers, "fixture", value -> {})) {
            try { checkpoint.replay(previous, digest, dataset, rows, (kind, row) -> {}, (unit,evidence) -> {}); throw new AssertionError(message); }
            catch (java.io.IOException expected) { }
        }
        require(!Files.exists(root.resolve("work")), "A rejected checkpoint published a dataset");
    }
    private static String repeat(char value) { char[] chars = new char[64]; Arrays.fill(chars, value); return new String(chars); }
    /** Catches replacing every replayed unit's RNG boundary with the old
     * terminal state: a second interruption must resume the shorter prefix,
     * including the cached Gaussian, and emit the ordinary Source bytes. */
    private static void nativeReplayInterrupted(Path root, JsonObject proof, List<String> handlers,
                                                JsonObject environment) throws Exception {
        Files.createDirectories(root);
        java.util.Random nativeRandom = new java.util.Random(513), oracle = new java.util.Random(513);
        nativeRandom.nextGaussian(); oracle.nextGaussian();
        RendererRandom.State[] pending = {null};
        JsonObject[] receipt = {null};
        JsonObject[] expectedRows = new JsonObject[3];
        Path original = root.resolve("original"), interrupted = root.resolve("interrupted"), finalAttempt = root.resolve("final");
        try (Continuations<RendererRandom.State> states = new Continuations<>(4)) {
            states.begin(original);
            try (RendererRandom.Session random = new RendererRandom.Session();
                 Checkpoints checkpoint = new Checkpoints(original, proof, handlers, "fixture", value -> {
                     states.save(original, value.get("sha256").getAsString(), value.get("units").getAsInt(), pending[0]);
                     receipt[0] = value;
                 })) {
                for (int unit = 0; unit < 3; unit++) {
                    double drawn;
                    try (RendererRandom.Scope scope = random.scope()) { drawn = RendererRandom.gaussian(nativeRandom); }
                    double wanted = oracle.nextGaussian();
                    require(Double.doubleToRawLongBits(drawn) == Double.doubleToRawLongBits(wanted), "Initial detached Gaussian changed");
                    expectedRows[unit] = nativeRow(unit, wanted);
                    checkpoint.begin(unit == 0 ? "base" : handlers.get(unit - 1));
                    checkpoint.row("visuals", nativeRow(unit, drawn));
                    pending[0] = random.save(); checkpoint.finish(object());
                }
                checkpoint.release(true);
            }
            states.release(original, true);
            String originalHash = receipt[0].get("sha256").getAsString();
            Continuations.Lease<RendererRandom.State> originalStates = states.acquire(original, originalHash, 3);
            states.begin(interrupted);
            try (Dataset dataset = new Dataset(root.resolve("interrupted-work"), "fixture", environment, false);
                 Rows rows = new Rows(root.resolve("interrupted-sort"));
                 Checkpoints checkpoint = new Checkpoints(interrupted, proof, handlers, "fixture", value -> {
                     states.save(interrupted, value.get("sha256").getAsString(), value.get("units").getAsInt(), pending[0]);
                     receipt[0] = value;
                 })) {
                try {
                    checkpoint.replay(original, originalHash, dataset, rows, (kind, row) -> {}, (unit, evidence) -> {
                        if (unit == 2) throw new java.io.IOException("later native unit changed");
                    }, new Checkpoints.NativeReplay() {
                        @Override public void verify(Path previous, String digest, int units, boolean required) throws Exception {
                            require(required && states.acquire(previous, digest, units).size() == 3, "Native prefix lost its process capability");
                        }
                        @Override public void sealed(int unit) throws Exception { pending[0] = originalStates.state(unit); }
                    });
                    throw new AssertionError("A changed later native unit was reused");
                } catch (java.io.IOException expected) {
                    require(expected.getMessage().equals("later native unit changed"), "Wrong interruption failure");
                }
                checkpoint.release(true);
            }
            states.release(interrupted, true);
            require(!Files.exists(root.resolve("interrupted-work")), "Interrupted replay published a Source");
            String shorterHash = receipt[0].get("sha256").getAsString();
            Continuations.Lease<RendererRandom.State> shorter = states.acquire(interrupted, shorterHash, 2);
            require(shorter.terminal().positions().get(0).draws == 2, "Shorter prefix retained the old terminal draw position");
            require(shorter.terminal().positions().get(0).current.haveGaussian, "Shorter prefix discarded its cached Gaussian");
            String resumed;
            try (Dataset dataset = new Dataset(root.resolve("final-work"), "fixture", environment, false);
                 Rows rows = new Rows(root.resolve("final-sort"));
                 RendererRandom.Session random = new RendererRandom.Session();
                 Checkpoints checkpoint = new Checkpoints(finalAttempt, proof, handlers, "fixture", value -> {})) {
                int restored = checkpoint.replay(interrupted, shorterHash, dataset, rows, (kind, row) -> {}, (unit, evidence) -> {},
                        new Checkpoints.NativeReplay() {
                            @Override public void verify(Path previous, String digest, int units, boolean required) throws Exception {
                                require(required && states.acquire(previous, digest, units).size() == 2, "Shorter prefix capability was not checked");
                            }
                            @Override public void sealed(int unit) throws Exception { pending[0] = shorter.state(unit); }
                        });
                require(restored == 2, "Unfinished later unit leaked into the new prefix");
                random.restore(shorter.terminal(), (source, initial) -> require(source == nativeRandom, "Native Random owner changed"));
                double drawn;
                try (RendererRandom.Scope scope = random.scope()) { drawn = RendererRandom.gaussian(nativeRandom); }
                JsonObject last = nativeRow(2, drawn);
                require(last.equals(expectedRows[2]), "Resumed Gaussian skipped or repeated a draw");
                checkpoint.begin(handlers.get(1)); checkpoint.row("visuals", last); checkpoint.finish(object());
                rows.add("visuals", last); rows.write(dataset); resumed = dataset.seal(); checkpoint.release(true);
            }
            try (Dataset dataset = new Dataset(root.resolve("direct-work"), "fixture", environment, false);
                 Rows rows = new Rows(root.resolve("direct-sort"))) {
                for (JsonObject row : expectedRows) rows.add("visuals", row);
                rows.write(dataset); require(resumed.equals(dataset.seal()), "Interrupted native resume changed Source bytes");
            }
        }
        java.util.Random unchanged = new java.util.Random(513); unchanged.nextGaussian();
        require(Double.doubleToRawLongBits(nativeRandom.nextGaussian()) == Double.doubleToRawLongBits(unchanged.nextGaussian()),
                "Checkpoint recovery mutated the game's Random");
    }
    private static JsonObject nativeRow(int unit, double gaussian) {
        return object("id", "visual_" + unit, "data", object("kind", "javaRandom", "draw", Long.toHexString(Double.doubleToRawLongBits(gaussian))));
    }
    private static void continuations(Path root) throws Exception {
        try(Continuations<String> states=new Continuations<>(2)){
            Path first=root.resolve("first");states.begin(first);states.save(first,repeat('a'),1,"before-random");states.save(first,repeat('b'),2,"after-random");
            try{states.acquire(first,repeat('b'),2);throw new AssertionError("An active native attempt was reusable");}catch(java.io.IOException expected){}
            states.release(first,true);Continuations.Lease<String> proof=states.acquire(first,repeat('b'),2);
            require(proof.state(0).equals("before-random")&&proof.state(1).equals("after-random"),"The terminal state was substituted for an earlier Handler seal");
            for(String hash:new String[]{repeat('a'),repeat('0')})try{states.acquire(first,hash,2);throw new AssertionError("A nonterminal or invented hash acquired native authority");}catch(java.io.IOException expected){}
            try{states.acquire(first,repeat('b'),1);throw new AssertionError("A truncated unit count acquired native authority");}catch(java.io.IOException expected){}
            Path second=root.resolve("second");states.begin(second);states.save(second,repeat('c'),1,"unsafe");states.release(second,false);
            try{states.acquire(second,repeat('c'),1);throw new AssertionError("Failed native cleanup granted authority");}catch(java.io.IOException expected){}
            Path third=root.resolve("third");states.begin(third);states.save(third,repeat('d'),1,"new");states.release(third,true);
            try{states.acquire(first,repeat('b'),2);throw new AssertionError("An evicted capability was reconstructed from its hash");}catch(java.io.IOException expected){}
            require(proof.state(0).equals("before-random"),"Eviction revoked a lease already owned by an active replay");
        }
    }
    private static void semantic() throws Exception {
        Class<?> type = Class.forName("com.github.dcysteine.nesql.exporter.source.Semantic");
        java.lang.reflect.Method add = type.getMethod("add", String.class, JsonObject.class), finish = type.getMethod("finish");
        Object first = type.newInstance(), same = type.newInstance(), changed = type.newInstance();
        JsonObject source = object("id", "item_one", "icon", "asset_old", "tags", array("oreCopper"), "stackLimit", 64);
        add.invoke(first, "items", source);
        add.invoke(first, "assets", object("id", "visual-only"));
        source.addProperty("icon", "asset_new");
        add.invoke(same, "items", source);
        source.addProperty("stackLimit", 16);
        add.invoke(changed, "items", source);
        Object proof = finish.invoke(first);
        require(proof.equals(finish.invoke(same)), "Visual assets changed native semantic proof");
        require(!proof.equals(finish.invoke(changed)), "Same-ID native property drift escaped semantic proof");
        require(source.get("icon").getAsString().equals("asset_new"), "Semantic projection mutated original facts");
        Semantic research = new Semantic(), graphics = new Semantic(), knowledge = new Semantic();
        JsonObject study = object("id", "research_one", "parents", array("research_parent"), "completed", false, "icon", null, "texture", "old_art");
        research.add("research", study);
        study.addProperty("texture", "new_art"); graphics.add("research", study);
        study.addProperty("completed", true); knowledge.add("research", study);
        require(research.finish().equals(graphics.finish()), "Research display art changed its native semantic proof");
        require(!research.finish().equals(knowledge.finish()), "Research dependency or knowledge changes escaped recovery validation");
        Semantic reference = new Semantic(), alteredReference = new Semantic();
        JsonObject gene = object("id", "refrow_same", "data", object("kind", "gene", "allele", "native-a"));
        reference.add("references", gene);
        gene.getAsJsonObject("data").addProperty("allele", "native-b");
        alteredReference.add("references", gene);
        require(!reference.finish().equals(alteredReference.finish()), "Native reference drift escaped sealed recovery proof");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
