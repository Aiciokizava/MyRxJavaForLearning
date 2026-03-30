package rx.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Планировщик для I/O-операций (сетевые запросы, чтение файлов и т.д.).
 *
 * Использует CachedThreadPool — создаёт новые потоки по мере необходимости
 * и переиспользует освободившиеся. Подходит для задач, которые большую часть
 * времени ожидают (блокируются на I/O).
 *
 * Аналог Schedulers.io() в RxJava.
 */
public class IOThreadScheduler implements Scheduler {

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("RxIO-" + t.getId());
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
