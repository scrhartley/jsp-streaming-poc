package example.streaming.config.mvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import example.streaming.util.future.LazyDirectExecutorService;

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

        List<UpgradableFutureCollection<?>> iterables;
        if (executorService instanceof LazyDirectExecutorService) {
            // A CompletionService doesn't make sense for LazyDirectExecutorService
            // since either the work will be done on submit, or else
            // later we would hang when trying to take from it (see upgradeIterable below).
            iterables = Collections.emptyList();
        } else {
            iterables = model.values().stream()
                    .filter(UpgradableFutureCollection.class::isInstance)
                    .<UpgradableFutureCollection<?>>map(UpgradableFutureCollection.class::cast)
                    .filter(it -> it.getFuturesPreUpgrade().stream().anyMatch(future -> !future.isDone()))
                    .collect(Collectors.toList());
        }

        if (tasks.isEmpty() && iterables.isEmpty()) {
            return;
        }

        // Collect futures for possible cancellation.
        List<Future<?>> allFutures = Stream.concat(
                tasks.stream(),
                iterables.stream().flatMap(it -> it.getFuturesPreUpgrade().stream())
        ).collect(Collectors.toList());

        ReadWriteLock rwl = new ReentrantReadWriteLock();
        rwl.writeLock().lock();
        try {
            Lock readLock = rwl.readLock();
            if (!tasks.isEmpty()) {
                upgradeFutures(tasks, readLock);
            }
            if (!iterables.isEmpty()) {
                for (UpgradableFutureCollection<?> iterable : iterables) {
                    upgradeIterable(iterable, readLock);
                }
            }
        } catch (RuntimeException e) { // Mainly worried about RejectedExecutionException
            for (Future<?> task : allFutures) {
                task.cancel(true);
            }
            throw e;
        } finally {
            rwl.writeLock().unlock();
        }
    }


    private <T> void upgradeIterable(UpgradableFutureCollection<T> iterable, Lock readLock) {
        CompletionService<T> ecs = new ExecutorCompletionService<>(executorService);
        Submitter<T> submitter = ecs::submit;
        List<UpgradeableFuture<T>> tasks = iterable.getFuturesPreUpgrade();

        List<Future<T>> completed = new ArrayList<>();
        for (UpgradeableFuture<T> task : tasks) {
            boolean submitted = upgradeFuture(task, readLock, submitter);
            if (!submitted) {
                completed.add(task);
            }
        }
        int pendingCount = tasks.size() - completed.size();
        iterable.setUpgradedFutures(completed, pendingCount, ecs, timeoutSeconds);
    }

    @SuppressWarnings("unchecked")
    private void upgradeFutures(List<UpgradeableFuture<?>> tasks, Lock readLock) {
        // We have a design tension since an ExecutionService
        // will use the generics for the Callable that's passed to submit,
        // while a CompletionService has class generics.
        @SuppressWarnings("rawtypes")
        Submitter submitter = executorService::submit;
        for (UpgradeableFuture<?> task : tasks) {
            upgradeFuture(task, readLock, submitter);
        }
    }


    private interface Submitter<T> {
        Future<T> submit(Callable<T> task) throws RejectedExecutionException;
    }

    private <T> boolean upgradeFuture(
            UpgradeableFuture<T> task, Lock readLock, Submitter<T> submitter) throws RejectedExecutionException {
        // Extra check in case we are using a same-thread executor
        // and a task has been run by another that depends on it.
        if (task.isDone()) return false;

        Callable<T> callable = task.getCallable();
        Future<T> future = submitter.submit(() -> {
            // Block running callable until write lock has been released.
            // We don't want tasks that call each other to be inconsistent: try to upgrade all first.
            // (Note: if executor runs on writeLock thread then readLock will be granted immediately.)
            if (readLock.tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
                readLock.unlock();
                return callable.call();
            } else { // Executor is overloaded???
                throw new TimeoutException("Could not acquire read lock.");
            }
        });

        try {
            task.upgradeFuture(future);
            return true;
        } catch (RuntimeException e) {
            future.cancel(true);
            throw e;
        }
    }

}
