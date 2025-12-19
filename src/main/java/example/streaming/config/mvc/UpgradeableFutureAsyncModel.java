package example.streaming.config.mvc;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import example.streaming.AsyncModel;

class UpgradeableFutureAsyncModel extends WrappingModel implements AsyncModel {
    private final int timeoutSeconds;

    UpgradeableFutureAsyncModel(Model model, int timeoutSeconds) {
        super(model);
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public <T> AsyncValue<T> addAttribute(String attributeName, Callable<T> callable) {
        AsyncValue<T> future = new AsyncValueUpgradeableFuture<>(callable);
        super.addAttribute(attributeName, future);
        return future;
    }

    @Override
    public <T> void addUnordered(String attributeName, Callable<T>[] callables) {
        Collection<Future<T>> futures = new UpgradeableFutureCollection<>(callables, true);
        super.addAttribute(attributeName, futures);
    }

    @Override
    public AsyncModel addSubModel(String attributeName) {
        AsyncModel subModel = new SubModel();
        super.addAttribute(attributeName, subModel);
        return subModel;
    }


    private class AsyncValueUpgradeableFuture<T> extends UpgradeableFuture<T> implements AsyncValue<T> {
        AsyncValueUpgradeableFuture(Callable<T> callable) {
            super(callable);
        }

        // Allow other passed in callables calling this to catch their
        // business logic exceptions as if there wasn't a future involved.
        // For things like InterruptedExceptions and TimeoutException
        // it won't be obvious they're there, and they can just propagate.
        // Also ensure that there's always a timeout involved.
        @Override
        public T await() throws Exception {
            try {
                return get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception
                        && !(cause instanceof InterruptedException)
                        && !(cause instanceof CancellationException)) {
                    throw (Exception) cause;
                }
                throw e;
            }
        }
    }

    // Exists so that sub-models implement Map and so can be directly accessed in the template
    private class SubModel extends ExtendedModelMap implements AsyncModel, FutureContainer {
        @Override
        public <T> AsyncValue<T> addAttribute(String attributeName, Callable<T> callable) {
            AsyncValue<T> future = new AsyncValueUpgradeableFuture<>(callable);
            super.addAttribute(attributeName, future);
            return future;
        }

        @Override
        public <T> void addUnordered(String attributeName, Callable<T>[] callables) {
            Collection<Future<T>> futures = new UpgradeableFutureCollection<>(callables, true);
            super.addAttribute(attributeName, futures);
        }

        @Override
        public AsyncModel addSubModel(String attributeName) {
            throw new UnsupportedOperationException("Nested sub-models are not allowed");
        }


        @Override
        public void collectFutures(List<Future<?>> sink) {
            for (Object value : values()) {
                if (value instanceof Future) {
                    sink.add((Future<?>) value);
                } else if (value instanceof FutureContainer) {
                    ((FutureContainer) value).collectFutures(sink);
                }
            }
        }
    }

}


class WrappingModel implements Model {
    private final Model source;
    WrappingModel(Model model) {
        this.source = model;
    }

    @Override
    public Model addAttribute(String name, Object value) {
        source.addAttribute(name, value);
        return this;
    }
    @Override
    public Model addAttribute(Object value) {
        source.addAttribute(value);
        return this;
    }
    @Override
    public Model addAllAttributes(Collection<?> values) {
        source.addAllAttributes(values);
        return this;
    }
    @Override
    public Model addAllAttributes(Map<String, ?> attributes) {
        source.addAllAttributes(attributes);
        return this;
    }
    @Override
    public Model mergeAttributes(Map<String, ?> attributes) {
        source.mergeAttributes(attributes);
        return this;
    }
    @Override
    public boolean containsAttribute(String name) {
        return source.containsAttribute(name);
    }
    @Override
    public Object getAttribute(String name) {
        return source.getAttribute(name);
    }
    @Override
    public Map<String, Object> asMap() {
        return source.asMap();
    }
}
