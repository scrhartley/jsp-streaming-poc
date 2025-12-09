package example.streaming.config.mvc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
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

    public FutureUpgraderResult upgradeFutures(Map<String, ?> model) {
        if (model == null || model.isEmpty()) {
            return FutureUpgraderResult.empty();
        }

        @SuppressWarnings("unchecked")
        Map<UpgradeableFuture<Object>, String> ufAttributeLookup = model.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof UpgradeableFuture)
                .map(entry -> (Map.Entry<String, UpgradeableFuture<Object>>) entry)
                .collect(Collectors.toMap(
                        Map.Entry::getValue, Map.Entry::getKey,
                        (u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); },
                        LinkedHashMap::new)); // For predictability when using single thread executor.

        Collection<UpgradeableFuture<Object>> done;
        List<UpgradeableFuture<Object>> tasksToUpgrade;
        List<UpgradeableFutureCollection<?>> iterables;
        if (executorService instanceof LazyDirectExecutorService) {
            // A CompletionService doesn't make sense for LazyDirectExecutorService
            // since either the work will be done on submit, or else
            // later we would hang when trying to take from it.
            tasksToUpgrade = Collections.emptyList();
            done = ufAttributeLookup.keySet();
            iterables = Collections.emptyList();
        } else {
            Map<Boolean, List<UpgradeableFuture<Object>>> tasksByDone = ufAttributeLookup.keySet().stream()
                    .collect(Collectors.partitioningBy(Future::isDone));
            tasksToUpgrade = tasksByDone.get(false);
            done = tasksByDone.get(true);
            iterables = model.values().stream()
                    .filter(UpgradeableFutureCollection.class::isInstance)
                    .<UpgradeableFutureCollection<?>>map(UpgradeableFutureCollection.class::cast)
                    .filter(it -> it.getFuturesPreUpgrade().stream().anyMatch(future -> !future.isDone()))
                    .collect(Collectors.toList());
        }


        BlockingQueue<Future<Object>> completionQueue = new LinkedBlockingQueue<>(done);

        Map<Future<?>, UpgradeableFuture<?>> upgradedFutureLookup =
                (!tasksToUpgrade.isEmpty() || !iterables.isEmpty())
                        ? upgradeAll(tasksToUpgrade, iterables, completionQueue)
                        : Collections.emptyMap();
        // Also track CompletableFuture in order to provide support for futures not under our control.
        Map<CompletableFuture<Object>, String> cfAttributeLookup = trackCompletableFutures(model, completionQueue);

        if (ufAttributeLookup.isEmpty() && cfAttributeLookup.isEmpty()) {
            return FutureUpgraderResult.empty();
        }

        Map<Future<Object>, String> attributeLookup = new HashMap<>();
        attributeLookup.putAll(ufAttributeLookup);
        attributeLookup.putAll(cfAttributeLookup);
        return newResult(attributeLookup, completionQueue, upgradedFutureLookup, timeoutSeconds);
    }


    private Map<Future<?>, UpgradeableFuture<?>> upgradeAll(
            List<UpgradeableFuture<Object>> tasks, List<UpgradeableFutureCollection<?>> iterables,
            BlockingQueue<Future<Object>> completionQueue) {
        // Collect futures for possible cancellation.
        List<Future<?>> allFutures = Stream.concat(
                tasks.stream(),
                iterables.stream().flatMap(it -> it.getFuturesPreUpgrade().stream())
        ).collect(Collectors.toList());

        ReadWriteLock rwl = new ReentrantReadWriteLock();
        rwl.writeLock().lock();
        try {
            Lock readLock = rwl.readLock();

            Map<Future<?>, UpgradeableFuture<?>> mapping;
            if (!tasks.isEmpty()) {
                mapping = new HashMap<>();

                CompletionService<Object> completionService =
                        new ExecutorCompletionService<>(executorService, completionQueue);
                for (UpgradeableFuture<Object> task : tasks) {
                    Future<?> upgraded = upgradeFuture(task, readLock, completionService);
                    if (upgraded == null) {
                        completionQueue.add(task);
                    } else {
                        mapping.put(upgraded, task);
                    }
                }
            } else {
                mapping = Collections.emptyMap();
            }

            if (!iterables.isEmpty()) {
                for (UpgradeableFutureCollection<?> iterable : iterables) {
                    upgradeIterable(iterable, readLock);
                }
            }

            return mapping;
        } catch (RuntimeException e) { // Mainly worried about RejectedExecutionException
            for (Future<?> task : allFutures) {
                task.cancel(true);
            }
            throw e;
        } finally {
            rwl.writeLock().unlock();
        }
    }

    private <T> void upgradeIterable(UpgradeableFutureCollection<T> iterable, Lock readLock) {
        CompletionService<T> ecs = new ExecutorCompletionService<>(executorService);
        List<UpgradeableFuture<T>> tasks = iterable.getFuturesPreUpgrade();

        List<UpgradeableFuture<T>> completed = new ArrayList<>();
        Map<Future<T>, UpgradeableFuture<T>> pendingLookup = new HashMap<>();
        for (UpgradeableFuture<T> task : tasks) {
            Future<T> submitted = upgradeFuture(task, readLock, ecs);
            if (submitted == null) {
                completed.add(task);
            } else {
                pendingLookup.put(submitted, task);
            }
        }

        // Can't expose the CompletionService directly, since it doesn't return an UpgradeableFuture.
        UpgradeableFutureCollection.PendingQueue<T> queue = new UpgradeableFutureCollection.PendingQueue<>() {
            int remaining = tasks.size() - completed.size();

            @Override
            public UpgradeableFuture<T> take() {
                try {
                    Future<T> future = ecs.poll(timeoutSeconds, TimeUnit.SECONDS);
                    if (future == null) {
                        throw new RuntimeException(new TimeoutException());
                    }
                    remaining--;
                    return pendingLookup.get(future);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            @Override
            public int size() {
                return remaining;
            }
        };
        iterable.setUpgradedFutures(completed, queue);
    }

    private <T> Future<T> upgradeFuture(
            UpgradeableFuture<T> task, Lock readLock, CompletionService<T> submitter) throws RejectedExecutionException {
        // Extra check in case we are using a same-thread executor
        // and a task has been run by another that depends on it.
        if (task.isDone()) return null;

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
            return future;
        } catch (RuntimeException e) {
            future.cancel(true);
            throw e;
        }
    }


    private static FutureUpgraderResult newResult(
            Map<? extends Future<?>, String> attributeLookup, BlockingQueue<Future<Object>> completionQueue,
            Map<Future<?>, UpgradeableFuture<?>> upgradedFutureLookup, int timeoutSeconds) {
        Set<String> attributeNames = new HashSet<>(attributeLookup.values());
        return new FutureUpgraderResult(Collections.unmodifiableSet(attributeNames), new Iterable<>() {
            final List<String> allCompleted = new ArrayList<>();

            @Override
            public Iterator<String> iterator() {
                return new Iterator<>() {
                    final Iterator<String> doneIt = allCompleted.isEmpty()
                            ? Collections.emptyIterator() : new ArrayList<>(allCompleted).iterator(); // Snapshot
                    int pending = attributeLookup.size() - allCompleted.size();

                    @Override
                    public boolean hasNext() {
                        return doneIt.hasNext() || pending > 0;
                    }

                    @Override
                    public String next() {
                        if (doneIt.hasNext()) {
                            return doneIt.next();
                        } else if (pending == 0) {
                            throw new NoSuchElementException();
                        } else {
                            try {
                                return nextFromQueue();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    private String nextFromQueue() throws InterruptedException {
                        Future<?> future = completionQueue.poll(timeoutSeconds, TimeUnit.SECONDS);
                        if (future == null) {
                            throw new RuntimeException(new TimeoutException());
                        }
                        pending--;
                        UpgradeableFuture<?> upgradeableFuture = upgradedFutureLookup.get(future);
                        String attribute = attributeLookup.get(upgradeableFuture != null ? upgradeableFuture : future);
                        Objects.requireNonNull(attribute, "Something has gone wrong");
                        allCompleted.add(attribute);
                        return attribute;
                    }
                };
            }
        });
    }

    private static Map<CompletableFuture<Object>, String>
            trackCompletableFutures(Map<String, ?> model, BlockingQueue<Future<Object>> completionQueue) {
        Map<CompletableFuture<Object>, String> attributeLookup = null;
        for (Map.Entry<String, ?> entry : model.entrySet()) {
            if (entry.getValue() instanceof CompletableFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<Object> cf = (CompletableFuture<Object>) entry.getValue();
                cf.whenComplete((v, t) -> completionQueue.add(cf));

                if (attributeLookup == null) {
                    attributeLookup = new HashMap<>();
                }
                attributeLookup.put(cf, entry.getKey());
            }
        }
        return attributeLookup != null ? attributeLookup : Collections.emptyMap();
    }


    public static class FutureUpgraderResult {
        public static final String KEY = "mvc.model.future.tracked.state";

        private final Set<String> futureAttributeNames;
        private final Iterable<String> completionQueue;

        private FutureUpgraderResult(Set<String> futureAttributeNames, Iterable<String> completionQueue) {
            this.futureAttributeNames = futureAttributeNames;
            this.completionQueue = completionQueue;
        }

        public Set<String> getAttributeNames() {
            return futureAttributeNames;
        }

        public Iterable<String> getCompletionQueue() {
            return completionQueue;
        }


        static FutureUpgraderResult empty() {
            return new FutureUpgraderResult(Collections.emptySet(), Collections::emptyIterator);
        }
    }

}
