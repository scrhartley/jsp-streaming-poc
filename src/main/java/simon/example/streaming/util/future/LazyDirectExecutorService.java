package simon.example.streaming.util.future;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// A "fake" ExecutorService that instead relies upon the Futures to run themselves when get() is invoked.
// Using this ExecutorService allows deferring execution of a task without introducing concurrency.
public class LazyDirectExecutorService extends AbstractExecutorService {

    private volatile boolean shutdown;
    private volatile boolean taskRunning;
    private volatile boolean terminated;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();


    @Override
    public <T> Future<T> submit(Callable<T> task) {
        Objects.requireNonNull(task);
        return new LazyTask(task);
    }
    @Override
    public Future<?> submit(Runnable task) {
        Objects.requireNonNull(task);
        return new LazyTask(task, null);
    }
    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        Objects.requireNonNull(task);
        return new LazyTask(task, result);
    }


    @Override
    public void execute(Runnable command) {
        lock.lock();
        try {
            if (shutdown) {
                throw new RejectedExecutionException();
            }
            taskRunning = true;
        } finally {
            lock.unlock();
        }

        try {
            command.run();
        } finally {
            lock.lock();
            try {
                taskRunning = false;
                if (shutdown) {
                    terminated = true;
                    condition.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }


    @Override
    public void shutdown() {
        shutdown = true;

        lock.lock();
        try {
            terminated = !taskRunning;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return null;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        lock.lock();
        try {
            if (terminated) {
                return true;
            }
            condition.await(timeout, unit);
            return terminated;
        } finally {
            lock.unlock();
        }
    }

}
