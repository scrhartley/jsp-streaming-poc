package example.streaming.config.mvc;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import example.streaming.util.future.LazyTask;

public final class UpgradeableFuture<V> implements Future<V> {
    // Only light synchronization: different instances could call each other,
    // but we rely on the code that upgrades the futures to do so before they are used.
    private volatile Future<V> wrapped;
    private volatile Callable<V> callable;

    public UpgradeableFuture(Callable<V> callable) {
        this.callable = Objects.requireNonNull(callable);
        // Begin with a future that doesn't start any new threads
        // and then upgrade to one backed by a concurrent ExecutorService
        // once we have references to them all and can cancel them if necessary.
        this.wrapped = new LazyTask<>(callable);
    }

    Callable<V> getCallable() {
        if (callable == null) {
            throw new IllegalStateException("Attempted access after future is upgraded");
        }
        return callable;
    }

    void upgradeFuture(Future<V> future) {
        if(this.callable == null) {
            throw new IllegalStateException("Already upgraded");
        }
        this.wrapped = future;
        this.callable = null;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return wrapped.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return wrapped.isCancelled();
    }

    @Override
    public boolean isDone() {
        return wrapped.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return wrapped.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return wrapped.get(timeout, unit);
    }
}
