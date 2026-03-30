package rx;

import rx.scheduler.Scheduler;
import rx.scheduler.IOThreadScheduler;
import rx.scheduler.ComputationScheduler;
import rx.scheduler.SingleThreadScheduler;

/**
 * Фабрика планировщиков. Предоставляет удобный доступ к стандартным Scheduler'ам.
 */
public final class Schedulers {

    private Schedulers() {
        // утилитарный класс
    }

    /**
     * @return планировщик для I/O-операций (CachedThreadPool)
     */
    public static Scheduler io() {
        return new IOThreadScheduler();
    }

    /**
     * @return планировщик для вычислений (FixedThreadPool, кол-во потоков = кол-во ядер)
     */
    public static Scheduler computation() {
        return new ComputationScheduler();
    }

    /**
     * @return планировщик с одним потоком
     */
    public static Scheduler single() {
        return new SingleThreadScheduler();
    }
}
