package rx.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Планировщик для вычислительных задач (CPU-bound).
 *
 * Использует FixedThreadPool с количеством потоков, равным числу доступных процессоров.
 * Подходит для математических вычислений, обработки данных и других задач,
 * которые активно используют CPU.
 *
 * Аналог {@code Schedulers.computation()} в RxJava.
 */
public class ComputationScheduler implements Scheduler {

    private final ExecutorService executor;

    public ComputationScheduler() {
        int poolSize = Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r);
            t.setName("RxComputation-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
