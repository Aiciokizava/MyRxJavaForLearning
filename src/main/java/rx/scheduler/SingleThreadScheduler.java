package rx.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Планировщик с одним потоком.
 *
 * Гарантирует последовательное выполнение задач — все задачи выполняются
 * строго одна за другой в одном и том же потоке.
 * Подходит для обновления UI или работы с ресурсами, не допускающими конкурентный доступ.
 *
 * Аналог Schedulers.single() в RxJava.
 */
public class SingleThreadScheduler implements Scheduler {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("RxSingle-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
