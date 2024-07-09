package org.example;

import com.github.rholder.retry.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.remoting.RemoteAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

@Component
public class TemporaryStorage {

    @Value("${bee.retry.times}")
    private int retryTimes = 3;

    private final ConcurrentHashMap<String, ValueWrapper> storage = new ConcurrentHashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // RetryerBuilder 构建重试实例 retryer,可以设置重试源且可以支持多个重试源，可以配置重试次数或重试超时时间，以及可以配置等待时间间隔
    private final Retryer<String> retryer = RetryerBuilder.<String> newBuilder()
            .retryIfExceptionOfType(Exception.class)//设置异常重试源
            .retryIfResult(res-> !StringUtils.hasText(res))  //设置根据结果重试
//            .withWaitStrategy(WaitStrategies.fixedWait(3, TimeUnit.SECONDS)) //设置等待间隔时间
            .withStopStrategy(StopStrategies.stopAfterAttempt(retryTimes)) //设置最大重试次数
            .build();

    public void test() throws ExecutionException, RetryException {
        String call = retryer.call(() -> {
            System.out.println("==========");
            return null;
        });
        System.out.println(call);
    }
    public String getValue(String key, Supplier<String> supplier) {
        ValueWrapper valueWrapper = storage.get(key);
        if (valueWrapper == null) {
            lock.lock();
            try {
                valueWrapper = storage.get(key);
                if (valueWrapper == null) {
                    String value = null;
                    try {
                        value = retryer.call(supplier::get);
                    } catch (ExecutionException | RetryException e) {
                        throw new RuntimeException(e);
                    }
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
        scheduler.schedule(() -> storage.remove(key), 1, TimeUnit.DAYS);
    }

    private static class ValueWrapper {
        final String value;

        ValueWrapper(String value) {
            this.value = value;
        }
    }
}
