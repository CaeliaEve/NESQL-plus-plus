package com.github.dcysteine.nesql.elysium.kernel;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ExportResourceManager implements AutoCloseable {
    private final Deque<ManagedResource> resources = new ArrayDeque<ManagedResource>();

    public <T> T add(String id, T resource, ResourceReleaser<T> releaser) {
        resources.push(new ManagedResource(id, resource, releaser));
        return resource;
    }

    public void addAction(String id, ResourceAction action) {
        resources.push(new ManagedResource(id, action, new ResourceReleaser<ResourceAction>() {
            @Override
            public void release(ResourceAction resource) throws Exception {
                resource.run();
            }
        }));
    }

    @Override
    public void close() throws Exception {
        close(null);
    }

    public void close(ReleaseObserver observer) throws Exception {
        Exception failure = null;
        while (!resources.isEmpty()) {
            ManagedResource resource = resources.pop();
            long startedAt = System.currentTimeMillis();
            try {
                resource.release();
                if (observer != null) {
                    observer.released(resource.id, "ok", System.currentTimeMillis() - startedAt);
                }
            } catch (Exception e) {
                if (observer != null) {
                    observer.released(resource.id, "failed", System.currentTimeMillis() - startedAt);
                }
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public interface ResourceReleaser<T> {
        void release(T resource) throws Exception;
    }

    public interface ResourceAction {
        void run() throws Exception;
    }

    public interface ReleaseObserver {
        void released(String id, String status, long elapsedMs);
    }

    private static final class ManagedResource {
        private final String id;
        private final Object resource;
        private final ResourceReleaser<Object> releaser;

        @SuppressWarnings("unchecked")
        private <T> ManagedResource(String id, T resource, ResourceReleaser<T> releaser) {
            this.id = id;
            this.resource = resource;
            this.releaser = (ResourceReleaser<Object>) releaser;
        }

        private void release() throws Exception {
            releaser.release(resource);
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
