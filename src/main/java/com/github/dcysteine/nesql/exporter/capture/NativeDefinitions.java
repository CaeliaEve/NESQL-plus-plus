package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Same-process ordered native definitions, without names, GUI callbacks or render state. */
final class NativeDefinitions implements AutoCloseable {
    private final String family;
    private final String changedCode;
    private final List<Object> records = new ArrayList<>();
    private final List<String> signatures = new ArrayList<>();
    private Supplier<? extends List<?>> current;
    private Function<Object, JsonElement> definition;

    NativeDefinitions(String family, List<?> source, Supplier<? extends List<?>> current,
                      Function<Object, JsonElement> definition) {
        this(family,source,current,definition,"recipe_changed");
    }
    NativeDefinitions(String family, List<?> source, Supplier<? extends List<?>> current,
                      Function<Object, JsonElement> definition,String changedCode) {
        if (source == null) throw new Jobs.Fault("handler_not_ready", family + " registry is unavailable");
        if (source.size() > 262144) throw new Jobs.Fault("recipe_limit", family + " registry exceeds its budget");
        this.family = family;
        if(!changedCode.equals("recipe_changed")&&!changedCode.equals("environment_changed"))throw new IllegalArgumentException("Invalid native drift classification");
        this.changedCode=changedCode;
        this.current = current;
        this.definition = definition;
        for (Object record : source) {
            Jobs.checkpoint();
            records.add(record);
            signatures.add(CanonicalJson.digest(definition.apply(record)));
        }
        finishVerification();
    }

    void verify() { source(); }

    Object record(int index) {
        List<?> source = source();
        return record(source, index);
    }

    private Object record(List<?> source, int index) {
        Jobs.checkpoint();
        Object record = records.get(index);
        if (source.get(index) != record || !signatures.get(index).equals(CanonicalJson.digest(definition.apply(record))))
            throw changed("definition or order changed at index " + index);
        return record;
    }

    void finishVerification() {
        List<?> source = source();
        for (int index = 0; index < records.size(); index++) record(source, index);
    }

    <T> T checked(int index, Supplier<T> operation) {
        record(index);
        T result;
        try { result = operation.get(); }
        catch (RuntimeException | Error failure) {
            try { record(index); }
            catch (RuntimeException | Error verification) { if (verification != failure) failure.addSuppressed(verification); }
            throw failure;
        }
        record(index);
        return result;
    }

    private List<?> source() {
        if (current == null) throw new Jobs.Fault("capture_closed", family + " native reader is closed");
        Jobs.checkpoint();
        List<?> source = current.get();
        if (source == null || source.size() != records.size()) throw changed("registry is unavailable or its count changed");
        return source;
    }

    private Jobs.Fault changed(String detail) { return new Jobs.Fault(changedCode, family + " native " + detail); }

    @Override public void close() {
        records.clear();
        signatures.clear();
        current = null;
        definition = null;
    }
}
