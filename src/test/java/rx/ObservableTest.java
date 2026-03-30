package rx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import rx.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ObservableTest {

    // ==================== 1. Базовые компоненты ====================

    @Test
    @DisplayName("create + subscribe: элементы доставляются наблюдателю")
    void testCreateAndSubscribe() {
        List<String> received = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable<String> observable = Observable.<String>create(emitter -> {
            emitter.onNext("Hello");
            emitter.onNext("World");
            emitter.onComplete();
        });

        observable.subscribe(new Observer<String>() {
            @Override
            public void onNext(String item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Unexpected error: " + t.getMessage());
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertEquals(List.of("Hello", "World"), received);
        assertTrue(completed.get());
    }

    @Test
    @DisplayName("create: пустой поток — сразу onComplete")
    void testEmptyStream() {
        AtomicBoolean completed = new AtomicBoolean(false);
        List<Object> received = new ArrayList<>();

        Observable.<Object>create(emitter -> {
            emitter.onComplete();
        }).subscribe(new Observer<Object>() {
            @Override
            public void onNext(Object item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Unexpected error");
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertTrue(received.isEmpty());
        assertTrue(completed.get());
    }

    @Test
    @DisplayName("после onComplete элементы не доставляются")
    void testNoEventsAfterComplete() {
        List<Integer> received = new ArrayList<>();

        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onComplete();
            emitter.onNext(2); // должен быть проигнорирован
        }).subscribe(new Observer<Integer>() {
            @Override
            public void onNext(Integer item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Unexpected error");
            }

            @Override
            public void onComplete() {
            }
        });

        assertEquals(List.of(1), received);
    }

    // ==================== 2. Операторы преобразования ====================

    @Test
    @DisplayName("map: преобразование элементов")
    void testMap() {
        List<Integer> received = new ArrayList<>();

        Observable.<Integer>create(emitter -> {
                    emitter.onNext(1);
                    emitter.onNext(2);
                    emitter.onNext(3);
                    emitter.onComplete();
                }).map(x -> x * 10)
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                        received.add(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        fail("Unexpected error");
                    }

                    @Override
                    public void onComplete() {
                    }
                });

        assertEquals(List.of(10, 20, 30), received);
    }

    @Test
    @DisplayName("map: ошибка в mapper пробрасывается в onError")
    void testMapError() {
        AtomicReference<Throwable> error = new AtomicReference<>();

        Observable.<Integer>create(emitter -> {
                    emitter.onNext(1);
                    emitter.onNext(0);
                    emitter.onNext(2);
                    emitter.onComplete();
                }).map(x -> 10 / x)
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        error.set(t);
                    }

                    @Override
                    public void onComplete() {
                    }
                });

        assertNotNull(error.get());
        assertInstanceOf(ArithmeticException.class, error.get());
    }

    @Test
    @DisplayName("filter: фильтрация элементов")
    void testFilter() {
        List<Integer> received = new ArrayList<>();

        Observable.<Integer>create(emitter -> {
                    for (int i = 1; i <= 10; i++) {
                        emitter.onNext(i);
                    }
                    emitter.onComplete();
                }).filter(x -> x % 2 == 0)
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                        received.add(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        fail("Unexpected error");
                    }

                    @Override
                    public void onComplete() {
                    }
                });

        assertEquals(List.of(2, 4, 6, 8, 10), received);
    }

    @Test
    @DisplayName("filter: ни один элемент не проходит — пустой результат")
    void testFilterNonePass() {
        List<Integer> received = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        Observable.<Integer>create(emitter -> {
                    emitter.onNext(1);
                    emitter.onNext(3);
                    emitter.onNext(5);
                    emitter.onComplete();
                }).filter(x -> x % 2 == 0)
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                        received.add(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        fail("Unexpected error");
                    }

                    @Override
                    public void onComplete() {
                        completed.set(true);
                    }
                });

        assertTrue(received.isEmpty());
        assertTrue(completed.get());
    }

    @Test
    @DisplayName("цепочка map + filter")
    void testMapThenFilter() {
        List<Integer> received = new ArrayList<>();

        Observable.<Integer>create(emitter -> {
                    for (int i = 1; i <= 5; i++) {
                        emitter.onNext(i);
                    }
                    emitter.onComplete();
                }).map(x -> x * 3)
                .filter(x -> x > 7)
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                        received.add(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        fail("Unexpected error");
                    }

                    @Override
                    public void onComplete() {
                    }
                });

        assertEquals(List.of(9, 12, 15), received);
    }

    // ==================== 3. flatMap ====================

    @Test
    @DisplayName("flatMap: разворачивание вложенных Observable")
    void testFlatMap() {
        List<String> received = new ArrayList<>();

        Observable.<String>create(emitter -> {
            emitter.onNext("Hello");
            emitter.onNext("World");
            emitter.onComplete();
        }).flatMap(word -> Observable.<String>create(innerEmitter -> {
            for (char c : word.toCharArray()) {
                innerEmitter.onNext(String.valueOf(c));
            }
            innerEmitter.onComplete();
        })).subscribe(new Observer<String>() {
            @Override
            public void onNext(String item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
                fail("Unexpected error");
            }

            @Override
            public void onComplete() {
            }
        });

        assertEquals(List.of("H", "e", "l", "l", "o", "W", "o", "r", "l", "d"), received);
    }

    @Test
    @DisplayName("flatMap: ошибка во внутреннем Observable пробрасывается")
    void testFlatMapInnerError() {
        AtomicReference<Throwable> error = new AtomicReference<>();

        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onComplete();
        }).flatMap(x -> Observable.<Integer>create(inner -> {
            inner.onError(new RuntimeException("inner error"));
        })).subscribe(new Observer<Integer>() {
            @Override
            public void onNext(Integer item) {
                fail("Should not receive items");
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
            }

            @Override
            public void onComplete() {
            }
        });

        assertNotNull(error.get());
        assertEquals("inner error", error.get().getMessage());
    }

    // ==================== 4. Disposable ====================

    @Test
    @DisplayName("Disposable: отмена подписки прекращает доставку событий")
    void testDisposable() {
        List<Integer> received = new ArrayList<>();

        Disposable disposable = Observable.<Integer>create(emitter -> {
            for (int i = 1; i <= 100; i++) {
                if (emitter.isDisposed()) {
                    return;
                }
                emitter.onNext(i);
            }
            emitter.onComplete();
        }).subscribe(new Observer<Integer>() {
            @Override
            public void onNext(Integer item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });

        disposable.dispose();
        assertTrue(disposable.isDisposed());
    }

    @Test
    @DisplayName("Disposable: отмена внутри onNext останавливает поток")
    void testDisposeInsideOnNext() {
        List<Integer> received = new ArrayList<>();
        final Disposable[] holder = new Disposable[1];

        holder[0] = Observable.<Integer>create(emitter -> {
            for (int i = 1; i <= 10; i++) {
                if (emitter.isDisposed()) {
                    return;
                }
                emitter.onNext(i);
            }
            emitter.onComplete();
        }).subscribe(new Observer<Integer>() {
            @Override
            public void onNext(Integer item) {
                received.add(item);
                if (item == 3) {
                    holder[0].dispose();
                }
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });

        assertEquals(List.of(1, 2, 3), received);
    }

    // ==================== 5. Обработка ошибок ====================

    @Test
    @DisplayName("Ошибка в источнике доставляется в onError")
    void testErrorInSource() {
        AtomicReference<Throwable> error = new AtomicReference<>();

        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            throw new RuntimeException("source error");
        }).subscribe(new Observer<Integer>() {
            @Override
            public void onNext(Integer item) {
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
            }

            @Override
            public void onComplete() {
            }
        });

        assertNotNull(error.get());
        assertEquals("source error", error.get().getMessage());
    }

    @Test
    @DisplayName("После onError элементы не доставляются")
    void testNoEventsAfterError() {
        List<Integer> received = new ArrayList<>();

        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onError(new RuntimeException("fail"));
            emitter.onNext(2);
        }).subscribe(new Observer<Integer>() {
            @Override
            public void onNext(Integer item) {
                received.add(item);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });

        assertEquals(List.of(1), received);
    }

    // ==================== 6. Schedulers ====================

    @Test
    @DisplayName("subscribeOn: подписка выполняется в другом потоке")
    void testSubscribeOn() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        String mainThread = Thread.currentThread().getName();

        Scheduler ioScheduler = Schedulers.io();

        Observable.<Integer>create(emitter -> {
                    threadName.set(Thread.currentThread().getName());
                    emitter.onNext(1);
                    emitter.onComplete();
                }).subscribeOn(ioScheduler)
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timeout waiting for completion");
        assertNotNull(threadName.get());
        assertNotEquals(mainThread, threadName.get(),
                "Подписка должна выполняться НЕ в главном потоке");

        ioScheduler.shutdown();
    }

    @Test
    @DisplayName("observeOn: обработка элементов в другом потоке")
    void testObserveOn() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> observerThread = new AtomicReference<>();
        String mainThread = Thread.currentThread().getName();

        Scheduler singleScheduler = Schedulers.single();

        Observable.<Integer>create(emitter -> {
                    emitter.onNext(42);
                    emitter.onComplete();
                }).observeOn(singleScheduler)
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                        observerThread.set(Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable t) {
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timeout waiting for completion");
        assertNotNull(observerThread.get());
        assertNotEquals(mainThread, observerThread.get(),
                "Обработка должна выполняться НЕ в главном потоке");

        singleScheduler.shutdown();
    }

    @Test
    @DisplayName("subscribeOn + observeOn: подписка и обработка в разных потоках")
    void testSubscribeOnAndObserveOn() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> sourceThread = new AtomicReference<>();
        AtomicReference<String> observerThread = new AtomicReference<>();

        Scheduler ioScheduler = Schedulers.io();
        Scheduler singleScheduler = Schedulers.single();

        Observable.<Integer>create(emitter -> {
                    sourceThread.set(Thread.currentThread().getName());
                    emitter.onNext(1);
                    emitter.onComplete();
                }).subscribeOn(ioScheduler)
                .observeOn(singleScheduler)
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                        observerThread.set(Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable t) {
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timeout");
        assertNotNull(sourceThread.get());
        assertNotNull(observerThread.get());
        assertNotEquals(sourceThread.get(), observerThread.get(),
                "Источник и наблюдатель должны работать в РАЗНЫХ потоках");

        ioScheduler.shutdown();
        singleScheduler.shutdown();
    }

    @Test
    @DisplayName("ComputationScheduler: задачи выполняются")
    void testComputationScheduler() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Integer> received = Collections.synchronizedList(new ArrayList<>());

        Scheduler compScheduler = Schedulers.computation();

        Observable.<Integer>create(emitter -> {
                    for (int i = 1; i <= 5; i++) {
                        emitter.onNext(i);
                    }
                    emitter.onComplete();
                }).subscribeOn(compScheduler)
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onNext(Integer item) {
                        received.add(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(1, 2, 3, 4, 5), received);

        compScheduler.shutdown();
    }

    // ==================== 7. Интеграционный тест ====================

    @Test
    @DisplayName("Полная цепочка: create -> map -> filter -> subscribeOn -> observeOn")
    void testFullChain() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> received = Collections.synchronizedList(new ArrayList<>());

        Scheduler ioScheduler = Schedulers.io();
        Scheduler singleScheduler = Schedulers.single();

        Observable.<Integer>create(emitter -> {
                    for (int i = 1; i <= 10; i++) {
                        emitter.onNext(i);
                    }
                    emitter.onComplete();
                }).map(x -> x * 2)
                .filter(x -> x > 10)
                .map(x -> "val=" + x)
                .subscribeOn(ioScheduler)
                .observeOn(singleScheduler)
                .subscribe(new Observer<String>() {
                    @Override
                    public void onNext(String item) {
                        received.add(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        fail("Unexpected error: " + t.getMessage());
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timeout");
        assertEquals(List.of("val=12", "val=14", "val=16", "val=18", "val=20"), received);

        ioScheduler.shutdown();
        singleScheduler.shutdown();
    }
}
