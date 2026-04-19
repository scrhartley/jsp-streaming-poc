package example.streaming.config.mvc;

import java.util.AbstractMap.SimpleImmutableEntry;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import example.streaming.AsyncModel;
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

        Map<String, Future<?>> asyncModelProxies = getAsyncModelAttributesAsFutures(model);

        Map<UpgradeableFuture<Object>, Set<String>> ufAttributeLookup = getUpgradeableFutureEntries(model, asyncModelProxies)
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        LinkedHashMap::new, // For predictability when using single thread executor.
                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));

        Collection<UpgradeableFuture<Object>> done;
        List<UpgradeableFuture<Object>> tasksToUpgrade;
        List<UpgradeableFutureCollection<?>> containers;
        if (executorService instanceof LazyDirectExecutorService) {
            // A CompletionService doesn't make sense for LazyDirectExecutorService
            // since either the work will be done on submit, or else
            // later we would hang when trying to take from it.
            tasksToUpgrade = Collections.emptyList();
            done = ufAttributeLookup.keySet();
            containers = Collections.emptyList();
        } else {
            Map<Boolean, List<UpgradeableFuture<Object>>> tasksByDone = ufAttributeLookup.keySet().stream()
                    .collect(Collectors.partitioningBy(Future::isDone));
            tasksToUpgrade = tasksByDone.get(false);
            done = tasksByDone.get(true);
            containers = model.values().stream()
                    .filter(UpgradeableFutureCollection.class::isInstance)
                    .<UpgradeableFutureCollection<?>>map(UpgradeableFutureCollection.class::cast)
                    .filter(c -> c.getOriginalFutures().stream().anyMatch(future -> !future.isDone()))
                    .collect(Collectors.toList());
        }


        BlockingQueue<Future<Object>> completionQueue = new LinkedBlockingQueue<>(done);

        Map<Future<?>, UpgradeableFuture<Object>> upgradedFutureLookup =
                (!tasksToUpgrade.isEmpty() || !containers.isEmpty())
                        ? upgradeAll(tasksToUpgrade, containers, completionQueue)
                        : Collections.emptyMap();
        // Also track CompletableFuture in order to provide support for futures not under our control.
        Map<CompletableFuture<?>, Set<String>> cfAttributeLookup =
                trackCompletableFutures(model, completionQueue, asyncModelProxies, null);

        if (ufAttributeLookup.isEmpty() && cfAttributeLookup.isEmpty()) {
            return FutureUpgraderResult.empty();
        }

        Map<Future<?>, Set<String>> attributeLookup = new HashMap<>();
        attributeLookup.putAll(ufAttributeLookup);
        attributeLookup.putAll(cfAttributeLookup);
        return newResult(attributeLookup, completionQueue, upgradedFutureLookup, timeoutSeconds);
    }


    private Map<Future<?>, UpgradeableFuture<Object>> upgradeAll(
            List<UpgradeableFuture<Object>> tasks, List<UpgradeableFutureCollection<?>> containers,
            BlockingQueue<Future<Object>> completionQueue) {
        // Collect futures for possible cancellation.
        List<Future<?>> allFutures = Stream.concat(
                tasks.stream(),
                containers.stream().flatMap(c -> c.getOriginalFutures().stream())
        ).collect(Collectors.toList());

        try {
            Map<Future<?>, UpgradeableFuture<Object>> mapping;
            if (!tasks.isEmpty()) {
                CompletionService<Object> completionService =
                        new ExecutorCompletionService<>(executorService, completionQueue);
                mapping = upgradeFutures(tasks, completionService, completionQueue);
            } else {
                mapping = Collections.emptyMap();
            }

            for (UpgradeableFutureCollection<?> container : containers) {
                upgradeContainer(container, new ExecutorCompletionService<>(executorService));
            }

            return mapping;
        }
        catch (RuntimeException e) { // Mainly worried about RejectedExecutionException
            for (Future<?> task : allFutures) {
                task.cancel(true);
            }
            throw e;
        }
    }

    private <T> void upgradeContainer(UpgradeableFutureCollection<T> container, CompletionService<T> cs) {
        List<UpgradeableFuture<T>> tasks = container.getOriginalFutures();

        List<UpgradeableFuture<T>> completed = new ArrayList<>();
        Map<Future<?>, UpgradeableFuture<T>> pendingLookup = upgradeFutures(tasks, cs, completed);

        // Can't expose the CompletionService directly, since it doesn't return an UpgradeableFuture.
        UpgradeableFutureCollection.PendingQueue<T> queue = new UpgradeableFutureCollection.PendingQueue<>() {
            int remaining = tasks.size() - completed.size();

            @Override
            public UpgradeableFuture<T> take() {
                try {
                    Future<T> future = cs.poll(timeoutSeconds, TimeUnit.SECONDS);
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
        container.setUpgradedFutures(completed, queue);
    }

    private static <T> Map<Future<?>, UpgradeableFuture<T>> upgradeFutures(
            Collection<UpgradeableFuture<T>> tasks,
            CompletionService<T> completionService, Collection<? super UpgradeableFuture<T>> completed)
            throws RejectedExecutionException {
        Map<Future<?>, UpgradeableFuture<T>> mapping = new HashMap<>();
        for (UpgradeableFuture<T> task : tasks) {
            Future<?> upgraded = upgradeFuture(task, completionService);
            if (upgraded == null) {
                completed.add(task);
            } else {
                mapping.put(upgraded, task);
            }
        }
        return mapping;
    }

    private static <T> Future<?> upgradeFuture(UpgradeableFuture<T> task, CompletionService<T> submitter) throws RejectedExecutionException {
        // Extra check in case we are using a same-thread executor
        // and a task has been run by another that depends on it.
        if (task.isDone()) return null;

        Future<?> future = submitter.submit(task, null);
        try {
            task.upgrade(future);
            return future;
        } catch (RuntimeException e) { // Shouldn't happen.
            future.cancel(true);
            throw e;
        }
    }


    private static FutureUpgraderResult newResult(
            Map<Future<?>, Set<String>> attributeLookup, BlockingQueue<Future<Object>> completionQueue,
            Map<Future<?>, UpgradeableFuture<Object>> upgradedFutureLookup, int timeoutSeconds) {
        Set<String> attributeNames = attributeLookup.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        return new FutureUpgraderResult(Collections.unmodifiableSet(attributeNames), new Iterable<>() {
            final List<Set<String>> allCompleted = new ArrayList<>();

            @Override
            public Iterator<Collection<String>> iterator() {
                return new Iterator<>() {
                    final Iterator<Set<String>> doneIt = allCompleted.isEmpty()
                            ? Collections.emptyIterator() : new ArrayList<>(allCompleted).iterator(); // Snapshot
                    int pending = attributeLookup.size() - allCompleted.size();

                    @Override
                    public boolean hasNext() {
                        return doneIt.hasNext() || pending > 0;
                    }

                    @Override
                    public Collection<String> next() {
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

                    private Set<String> nextFromQueue() throws InterruptedException {
                        Future<?> future = completionQueue.poll(timeoutSeconds, TimeUnit.SECONDS);
                        if (future == null) {
                            throw new RuntimeException(new TimeoutException());
                        }
                        pending--;
                        UpgradeableFuture<?> upgradeableFuture = upgradedFutureLookup.get(future);
                        Set<String> attributes = attributeLookup.get(upgradeableFuture != null ? upgradeableFuture : future);
                        Objects.requireNonNull(attributes, "Something has gone wrong");
                        allCompleted.add(attributes);
                        return attributes;
                    }
                };
            }
        });
    }

    private static Stream<Map.Entry<String, UpgradeableFuture<Object>>>
            getUpgradeableFutureEntries(Map<String, ?> model, Map<String, Future<?>> asyncModelProxyFutures) {
        @SuppressWarnings("unchecked")
        Stream<Map.Entry<String, UpgradeableFuture<Object>>> mainFutureEntries =
                Stream.concat(model.entrySet().stream(), asyncModelProxyFutures.entrySet().stream())
                        .filter(entry -> entry.getValue() instanceof UpgradeableFuture)
                        .map(entry -> (Map.Entry<String, UpgradeableFuture<Object>>) entry);

        @SuppressWarnings("unchecked")
        Stream<Map.Entry<String, UpgradeableFuture<Object>>> subFutureEntries = model.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof AsyncModel)
                .map(entry -> (Map.Entry<String, AsyncModel>) entry)
                .flatMap(entry -> {
                    String subKeyPrefix = entry.getKey() + '.';
                    return entry.getValue().asMap().entrySet().stream()
                            .filter(subEntry -> subEntry.getValue() instanceof UpgradeableFuture)
                            .map(subEntry -> {
                                String subKey = subKeyPrefix + subEntry.getKey();
                                @SuppressWarnings("unchecked")
                                UpgradeableFuture<Object> subValue = (UpgradeableFuture<Object>) subEntry.getValue();
                                return new SimpleImmutableEntry<>(subKey, subValue);
                            });
                });

        return Stream.concat(mainFutureEntries, subFutureEntries);
    }

    private static Map<CompletableFuture<?>, Set<String>>
            trackCompletableFutures(
                    Map<String, ?> model, BlockingQueue<Future<Object>> completionQueue,
                    Map<String, Future<?>> asyncModelProxyFutures, String subModelPrefix) {
        Map<CompletableFuture<?>, Set<String>> attributeLookup = new HashMap<>();

        Stream<Map.Entry<String, ?>> stream =
                Stream.concat(model.entrySet().stream(), asyncModelProxyFutures.entrySet().stream());
        stream.forEach(entry -> {
            Object value = entry.getValue();
            if (value instanceof CompletableFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<Object> cf = (CompletableFuture<Object>) value;

                String attrName = subModelPrefix == null ? entry.getKey() : subModelPrefix + entry.getKey();
                attributeLookup.compute(cf, (k, oldVal) -> {
                    if (oldVal == null) {
                        cf.whenComplete((v, t) -> completionQueue.add(cf));
                        return Collections.singleton(attrName);
                    } else { // Should be rare.
                        Set<String> result = (oldVal.size() == 1) ? new HashSet<>(oldVal) : oldVal;
                        result.add(attrName);
                        return result;
                    }
                });
            } else if (value instanceof AsyncModel) {
                Map<String, ?> subModel = ((AsyncModel) value).asMap();
                String prefix = entry.getKey() + '.';
                attributeLookup.putAll(trackCompletableFutures(
                        subModel, completionQueue, Collections.emptyMap(), prefix));
            }
        });

        return attributeLookup;
    }

    private Map<String, Future<?>> getAsyncModelAttributesAsFutures(Map<String, ?> model) {
        @SuppressWarnings("unchecked")
        Map<AsyncModel, List<String>> asyncModels = model.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof AsyncModel)
                .map(entry -> (Map.Entry<String, AsyncModel>) entry)
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        LinkedHashMap::new, // For predictability when using single thread executor.
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

        Map<String, Future<?>> resultsMap = new HashMap<>();
        for (Map.Entry<AsyncModel, List<String>> entry : asyncModels.entrySet()) {
            Map<String, ?> subModel = entry.getKey().asMap();

            List<UpgradeableFuture<?>> ufs = subModel.values().stream()
                    .filter(UpgradeableFuture.class::isInstance)
                    .<UpgradeableFuture<?>>map(UpgradeableFuture.class::cast)
                    .collect(Collectors.toList());

            CompletableFuture<?>[] cfs = subModel.values().stream()
                    .filter(CompletableFuture.class::isInstance)
                    .<CompletableFuture<?>>map(CompletableFuture.class::cast)
                    .toArray(CompletableFuture[]::new);

            Future<?> future;
            if (!ufs.isEmpty()) {
                future = new UpgradeableFuture<>(() -> {
                    for (UpgradeableFuture<?> uf : ufs) {
                        uf.get(timeoutSeconds, TimeUnit.SECONDS);
                    }
                    for (CompletableFuture<?> cf : cfs) {
                        cf.get(timeoutSeconds, TimeUnit.SECONDS);
                    }
                    return null;
                });
            } else {
                future = CompletableFuture.allOf(cfs);
            }

            for (String attrName : entry.getValue()) {
                resultsMap.put(attrName, future);
            }
        }
        return resultsMap;
    }


    public static class FutureUpgraderResult {
        public static final String KEY = "mvc.model.future.tracked.state";

        private final Set<String> futureAttributeNames;
        private final Iterable<Collection<String>> completionQueue;

        private FutureUpgraderResult(Set<String> futureAttributeNames, Iterable<Collection<String>> completionQueue) {
            this.futureAttributeNames = futureAttributeNames;
            this.completionQueue = completionQueue;
        }

        public Set<String> getAttributeNames() {
            return futureAttributeNames;
        }

        public Iterator<Collection<String>> completionIterator() {
            return completionQueue.iterator();
        }


        static FutureUpgraderResult empty() {
            return new FutureUpgraderResult(Collections.emptySet(), Collections::emptyIterator);
        }
    }

}
