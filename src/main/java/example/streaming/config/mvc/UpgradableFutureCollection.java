package example.streaming.config.mvc;

import static java.util.Collections.*;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UpgradableFutureCollection<T> extends AbstractCollection<Future<T>> {

    private List<UpgradeableFuture<T>> futures;
    private Collection<Future<T>> upgraded;

    UpgradableFutureCollection(Callable<T>[] callables) {
        this.futures = Stream.of(callables)
                .map(UpgradeableFuture::new)
                .collect(Collectors.toList());
    }

    @Override
    public Iterator<Future<T>> iterator() {
        if (upgraded != null) {
            return upgraded.iterator();
        } else {
            return Collections.<Future<T>>unmodifiableList(futures).iterator();
        }
    }

    @Override
    public int size() {
        if (upgraded != null) {
            return upgraded.size();
        } else {
            return futures.size();
        }
    }

    public List<UpgradeableFuture<T>> getFuturesPreUpgrade() {
        if (futures == null) {
            throw new IllegalStateException("Attempted access after upgrade");
        }
        return futures;
    }

    public void setUpgradedFutures(
            List<? extends Future<T>> completed, int pending,
            CompletionService<T> queue, int timeoutSeconds) {
        if (upgraded != null) {
            throw new IllegalStateException("Already set");
        }
        upgraded = new UpgradedCollection<>(completed, pending, queue, timeoutSeconds);
        futures = null; // Allow GC.
    }


    private static class UpgradedCollection<T> extends AbstractCollection<Future<T>> {
        private final List<Future<T>> allCompleted;
        private final CompletionService<T> queue;
        private final int timeoutSeconds;

        private int pendingCount;
        private UpgradedIterator activeIterator; // Only latest snapshot is valid.

        private UpgradedCollection(
                List<? extends Future<T>> preCompleted, int pending,
                CompletionService<T> queue, int timeoutSeconds) {
            this.allCompleted = new ArrayList<>(preCompleted);
            this.queue = queue;
            this.timeoutSeconds = timeoutSeconds;
            this.pendingCount = pending;
        }

        @Override
        public int size() {
            return pendingCount + allCompleted.size();
        }

        @Override
        public Iterator<Future<T>> iterator() {
            UpgradedIterator iterator = new UpgradedIterator();
            this.activeIterator = iterator;
            return iterator;
        }

        private void checkActive(UpgradedIterator iterator) {
            if (iterator != this.activeIterator) {
                throw new IllegalStateException("Attempted access of dead iterator");
            }
        }


        class UpgradedIterator implements Iterator<Future<T>> {
            final Iterator<Future<T>> completedIt = allCompleted.isEmpty()
                    ? emptyIterator() : new ArrayList<>(allCompleted).iterator(); // Snapshot

            @Override
            public boolean hasNext() {
                checkActive(this);
                return completedIt.hasNext() || pendingCount > 0;
            }

            @Override
            public Future<T> next() {
                checkActive(this);
                if (completedIt.hasNext()) {
                    return completedIt.next();
                } else if (pendingCount > 0) {
                    try {
                        Future<T> future = queue.poll(timeoutSeconds, TimeUnit.SECONDS);
                        if (future == null) {
                            throw new RuntimeException(new TimeoutException());
                        }
                        pendingCount--;
                        allCompleted.add(future);
                        return future;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new NoSuchElementException();
                }
            }
        }

    }

}
