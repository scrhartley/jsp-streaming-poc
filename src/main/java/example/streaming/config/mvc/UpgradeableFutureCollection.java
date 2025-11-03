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
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import example.streaming.util.future.LazyWrappedFuture;

public class UpgradeableFutureCollection<T> extends AbstractCollection<Future<T>> {

    private List<UpgradeableFuture<T>> futures;
    private Collection<Future<T>> upgraded;
    private final boolean extraLazy;

    UpgradeableFutureCollection(Callable<T>[] callables) {
        this(callables, false);
    }
    UpgradeableFutureCollection(Callable<T>[] callables, boolean extraLazy) {
        this.futures = Stream.of(callables)
                .map(UpgradeableFuture::new)
                .collect(Collectors.toList());
        this.extraLazy = extraLazy;
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

    public void setUpgradedFutures(List<UpgradeableFuture<T>> completed, PendingQueue<T> queue) {
        if (upgraded != null) {
            throw new IllegalStateException("Already set");
        }
        upgraded = new UpgradedCollection<>(completed, queue, extraLazy);
        futures = null; // Allow GC.
    }

    interface PendingQueue<T> {
        UpgradeableFuture<T> take();

        int size();
    }


    private static class UpgradedCollection<T> extends AbstractCollection<Future<T>> {
        private final List<UpgradeableFuture<T>> allCompleted;
        private final PendingQueue<T> queue;
        private final boolean extraLazy;

        private UpgradedIterator activeIterator; // Only latest snapshot is valid.

        private UpgradedCollection(List<UpgradeableFuture<T>> preCompleted, PendingQueue<T> queue, boolean extraLazy) {
            this.allCompleted = new ArrayList<>(preCompleted);
            this.queue = queue;
            this.extraLazy = extraLazy;
        }

        @Override
        public int size() {
            return queue.size() + allCompleted.size();
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
            final Iterator<UpgradeableFuture<T>> completedIt = allCompleted.isEmpty()
                    ? emptyIterator() : new ArrayList<>(allCompleted).iterator(); // Snapshot
            int queued = queue.size();

            @Override
            public boolean hasNext() {
                checkActive(this);
                return completedIt.hasNext() || queued > 0;
            }

            @Override
            public Future<T> next() {
                checkActive(this);
                if (completedIt.hasNext()) {
                    return completedIt.next();
                } else if (queued > 0) {
                    queued--;
                    if (extraLazy) {
                        // This means that the Future TypeConverter can see an
                        // uncompleted Future and do a flush before waiting for it to complete.
                        return new LazyWrappedFuture<>(() -> {
                            checkActive(UpgradedIterator.this);
                            return nextFromQueue();
                        });
                    } else {
                        return nextFromQueue();
                    }
                } else {
                    throw new NoSuchElementException();
                }
            }

            private Future<T> nextFromQueue() {
                UpgradeableFuture<T> future = queue.take();
                allCompleted.add(future);
                return future;
            }
        }

    }

}
