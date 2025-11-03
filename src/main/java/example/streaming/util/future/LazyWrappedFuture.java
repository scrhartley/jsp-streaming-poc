package example.streaming.util.future;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import example.streaming.util.cache.LazyValue;

public class LazyWrappedFuture<V> implements Future<V> {

    private final LazyValue<Future<V>> lazy;

    public LazyWrappedFuture(Supplier<Future<V>> supplier) {
        this.lazy = new LazyValue<>(supplier);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return lazy.value().cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return lazy.value().isCancelled();
    }

    @Override
    public boolean isDone() {
        return lazy.value().isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return lazy.value().get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return lazy.value().get(timeout, unit);
    }

}
