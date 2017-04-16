package org.javacs;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class EvictingExecutorTest {
    @Test
    public void evict() throws InterruptedException, ExecutionException, TimeoutException {
        EvictingExecutor exec = new EvictingExecutor();
        int[] tasks = {0, 0, 0};

        Future<?> one = exec.submit(() -> {
            sleep(10);
            tasks[0]++;
        });
        Future<?> two = exec.submit(() -> {
            sleep(10);
            tasks[1]++;
        });
        Future<?> three = exec.submit(() -> {
            sleep(10);
            tasks[2]++;
        });

        one.get(1, TimeUnit.SECONDS);
        two.get(1, TimeUnit.SECONDS);
        three.get(1, TimeUnit.SECONDS);

        assertThat("Task 1 ran", tasks[0], equalTo(1));
        assertThat("Task 2 was evicted", tasks[1], equalTo(0));
        assertThat("Task 3 ran", tasks[2], equalTo(1));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}