package example.streaming.util.cache;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public class LazyValue<V> {
    final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    final Supplier<V> supplier;
    boolean cacheValid; // Not volatile in ReentrantReadWriteLock Javadoc after Java 8.
    V item;

    public LazyValue(Supplier<V> supplier) {
        this.supplier = supplier;
    }

    public V value() {
        rwl.readLock().lock();
        if (!cacheValid) {
            // Must release read lock before acquiring write lock
            rwl.readLock().unlock();
            rwl.writeLock().lock();
            try {
                // Recheck state because another thread might have
                // acquired write lock and changed state before we did.
                if (!cacheValid) {
                    item = supplier.get();
                    cacheValid = true;
                }
                // Downgrade by acquiring read lock before releasing write lock
                rwl.readLock().lock();
            } finally {
                rwl.writeLock().unlock(); // Unlock write, still hold read
            }
        }

        try {
            return item;
        } finally {
            rwl.readLock().unlock();
        }
    }
}
