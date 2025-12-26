package example.streaming.config.mvc;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import example.streaming.util.future.LazyTask;

public class UpgradeableFuture<V> extends LazyTask<V> {

    private volatile Future<?> runner;

    public UpgradeableFuture(Callable<V> callable) {
        super(callable);
    }

    public UpgradeableFuture(Runnable runnable, V result) {
        super(runnable, result);
    }

    public void upgrade(Future<?> runner) {
        if (this.runner != null) {
            throw new IllegalStateException("Already upgraded");
        }
        this.runner = Objects.requireNonNull(runner);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean selfCancelled = super.cancel(mayInterruptIfRunning);
        if (runner != null) {
            // This is really just here for completeness and if the main future
            // is cancelled the runner should have nothing left to do and so may be done already.
            // Either way this is just best-effort and not guaranteed.
            runner.cancel(mayInterruptIfRunning);
        }
        return selfCancelled;
    }

}
