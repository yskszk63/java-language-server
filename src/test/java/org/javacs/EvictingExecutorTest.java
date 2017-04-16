package org.javacs;

import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class EvictingExecutorTest {
    @Test
    public void evict() throws InterruptedException, ExecutionException, TimeoutException {
        EvictingExecutor exec = new EvictingExecutor();
        int[] tasks = {0, 0, 0};

        Future<?> one = exec.submit(() -> {
            sleep(10);
            tasks[0]++;
        });

        sleep(1); // gives one a chance to start

        Future<?> two = exec.submit(() -> {
            sleep(10);
            tasks[1]++;
        });

        sleep(1); // Not long enough for one to finish

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