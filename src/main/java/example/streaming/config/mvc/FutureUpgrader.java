package example.streaming.config.mvc;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class FutureUpgrader {

    private final ExecutorService executorService;
    private final int timeoutSeconds;

    public FutureUpgrader(ExecutorService executorService, int timeoutSeconds) {
        this.executorService = Objects.requireNonNull(executorService);
        this.timeoutSeconds = timeoutSeconds;
    }

    public void upgradeFutures(Map<String, ?> model) {
        if (model == null || model.isEmpty()) {
            return;
        }

        List<UpgradeableFuture<?>> tasks = model.values().stream()
                .filter(UpgradeableFuture.class::isInstance)
                .<UpgradeableFuture<?>>map(UpgradeableFuture.class::cast)
                .filter(future -> !future.isDone())
                .collect(Collectors.toList());
        if (tasks.isEmpty()) {
            return;
        }

        ReadWriteLock rwl = new ReentrantReadWriteLock();
        rwl.writeLock().lock();
        try {
            for (UpgradeableFuture<?> task : tasks) {
                upgradeFuture(task, rwl);
            }
        } catch (RuntimeException e) { // Mainly worried about RejectedExecutionException
            for (UpgradeableFuture<?> task : tasks) {
                task.cancel(true);
            }
            throw e;
        } finally {
            rwl.writeLock().unlock();
        }
    }

    private <T> void upgradeFuture(
            UpgradeableFuture<T> task, ReadWriteLock rwl) throws RejectedExecutionException {
        // Extra check in case we are using a same-thread executor
        // and a task has been run by another that depends on it.
        if (task.isDone()) return;

        Callable<T> callable = task.getCallable();
        Future<T> future = executorService.submit(() -> {
            // Block running callable until write lock has been released.
            // We don't want tasks that call each other to be inconsistent: try to upgrade all first.
            // (Note: if executor runs on writeLock thread then readLock will be granted immediately.)
            if (rwl.readLock().tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
                rwl.readLock().unlock();
                return callable.call();
            } else { // Executor is overloaded???
                throw new TimeoutException("Could not acquire read lock.");
            }
        });

        try {
            task.upgradeFuture(future);
        } catch (RuntimeException e) {
            future.cancel(true);
            throw e;
        }
    }

}
