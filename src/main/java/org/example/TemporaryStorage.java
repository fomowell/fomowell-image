package org.example;

import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

@Component
public class TemporaryStorage {

    private final ConcurrentHashMap<String, ValueWrapper> storage = new ConcurrentHashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public String getValue(String key, Supplier<String> remoteRequest) {
        ValueWrapper valueWrapper = storage.get(key);
        if (valueWrapper == null) {
            lock.lock();
            try {
                valueWrapper = storage.get(key);
                if (valueWrapper == null) {
                    String value = remoteRequest.get();
                    valueWrapper = new ValueWrapper(value);
                    storage.put(key, valueWrapper);
                    scheduleRemoval(key);
                }
            } finally {
                lock.unlock();
            }
        }
        return valueWrapper.value;
    }

    private void scheduleRemoval(String key) {
        scheduler.schedule(() -> storage.remove(key), 3, TimeUnit.MINUTES);
    }

    private static class ValueWrapper {
        final String value;

        ValueWrapper(String value) {
            this.value = value;
        }
    }
}
