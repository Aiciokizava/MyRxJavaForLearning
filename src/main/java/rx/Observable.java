package rx;

import rx.scheduler.Scheduler;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Основной класс реактивного потока (аналог io.reactivex.Observable).
 *
 * Поддерживает:
 * - создание потока через create()
 * - операторы преобразования: map, filter, flatMap
 * - управление потоками: subscribeOn, observeOn
 * - отмену подписки через Disposable
 *
 * @param <T> тип элементов потока
 */
public class Observable<T> {

    /** Источник данных, вызываемый при подписке */
    private final ObservableOnSubscribe<T> source;

    private Observable(ObservableOnSubscribe<T> source) {
        this.source = source;
    }

    // ======================== Создание ========================

    /**
     * Создаёт Observable из пользовательского источника данных.
     *
     * Пример:
     *   Observable.create(emitter -> {
     *       emitter.onNext("Hello");
     *       emitter.onComplete();
     *   });
     *
     * @param source источник данных
     * @param <T>    тип элементов
     * @return новый Observable
     */
    public static <T> Observable<T> create(ObservableOnSubscribe<T> source) {
        return new Observable<>(source);
    }

    // ======================== Подписка ========================

    /**
     * Подписывает наблюдателя на этот поток.
     *
     * @param observer наблюдатель
     * @return Disposable для отмены подписки
     */
    public Disposable subscribe(Observer<T> observer) {
        ObservableEmitter<T> emitter = new ObservableEmitter<>(observer);
        try {
            source.subscribe(emitter);
        } catch (Throwable t) {
            emitter.onError(t);
        }
        return emitter;
    }

    /**
     * Упрощённая подписка — только обработка элементов.
     * Ошибки выбрасываются как RuntimeException, завершение игнорируется.
     *
     * @param onNext обработчик элементов
     * @return Disposable для отмены подписки
     */
    public Disposable subscribe(java.util.function.Consumer<T> onNext) {
        return subscribe(new Observer<T>() {
            @Override
            public void onNext(T item) {
                onNext.accept(item);
            }

            @Override
            public void onError(Throwable t) {
                throw new RuntimeException("Unhandled error in Observable", t);
            }

            @Override
            public void onComplete() {
                // игнорируем
            }
        });
    }

    // ======================== Операторы преобразования ========================

    /**
     * Преобразует каждый элемент потока с помощью функции mapper.
     *
     * Пример:
     *   observable.map(x -> x * 2).subscribe(System.out::println);
     *
     * Если mapper выбрасывает исключение, оно передаётся в onError.
     *
     * @param mapper функция преобразования
     * @param <R>    тип результата
     * @return новый Observable с преобразованными элементами
     */
    public <R> Observable<R> map(Function<T, R> mapper) {
        Observable<T> upstream = this;
        return new Observable<>(emitter -> {
            upstream.subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    try {
                        R result = mapper.apply(item);
                        emitter.onNext(result);
                    } catch (Throwable t) {
                        emitter.onError(t);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    emitter.onError(t);
                }

                @Override
                public void onComplete() {
                    emitter.onComplete();
                }
            });
        });
    }

    /**
     * Фильтрует элементы потока. Пропускает только те, для которых predicate возвращает true.
     *
     * Пример:
     *   observable.filter(x -> x > 5).subscribe(System.out::println);
     *
     * @param predicate условие фильтрации
     * @return новый Observable с отфильтрованными элементами
     */
    public Observable<T> filter(Predicate<T> predicate) {
        Observable<T> upstream = this;
        return new Observable<>(emitter -> {
            upstream.subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    try {
                        if (predicate.test(item)) {
                            emitter.onNext(item);
                        }
                    } catch (Throwable t) {
                        emitter.onError(t);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    emitter.onError(t);
                }

                @Override
                public void onComplete() {
                    emitter.onComplete();
                }
            });
        });
    }

    /**
     * Преобразует каждый элемент в новый Observable и разворачивает результаты
     * в единый поток.
     *
     * Пример:
     *   observable.flatMap(word -> Observable.create(e -> {
     *       for (char c : word.toCharArray()) e.onNext(c);
     *       e.onComplete();
     *   })).subscribe(System.out::println);
     *
     * Завершение внутреннего Observable не завершает внешний —
     * могут быть ещё элементы от upstream.
     *
     * @param mapper функция, возвращающая Observable для каждого элемента
     * @param <R>    тип элементов результирующего потока
     * @return новый Observable
     */
    public <R> Observable<R> flatMap(Function<T, Observable<R>> mapper) {
        Observable<T> upstream = this;
        return new Observable<>(emitter -> {
            upstream.subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    try {
                        Observable<R> inner = mapper.apply(item);
                        inner.subscribe(new Observer<R>() {
                            @Override
                            public void onNext(R innerItem) {
                                emitter.onNext(innerItem);
                            }

                            @Override
                            public void onError(Throwable t) {
                                emitter.onError(t);
                            }

                            @Override
                            public void onComplete() {
                                // внутренний поток завершился, но внешний продолжается
                            }
                        });
                    } catch (Throwable t) {
                        emitter.onError(t);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    emitter.onError(t);
                }

                @Override
                public void onComplete() {
                    emitter.onComplete();
                }
            });
        });
    }

    // ======================== Управление потоками ========================

    /**
     * Указывает, в каком Scheduler будет выполняться подписка (источник данных).
     * Влияет на то, в каком потоке вызывается source.subscribe().
     *
     * Пример:
     *   observable.subscribeOn(Schedulers.io()).subscribe(...);
     *
     * @param scheduler планировщик для выполнения подписки
     * @return новый Observable
     */
    public Observable<T> subscribeOn(Scheduler scheduler) {
        Observable<T> upstream = this;
        return new Observable<>(emitter -> {
            scheduler.execute(() -> {
                upstream.subscribe(new Observer<T>() {
                    @Override
                    public void onNext(T item) {
                        emitter.onNext(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        emitter.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        emitter.onComplete();
                    }
                });
            });
        });
    }

    /**
     * Указывает, в каком Scheduler будет происходить обработка элементов наблюдателем.
     * Каждый вызов onNext/onError/onComplete переключается на указанный поток.
     *
     * Пример:
     *   observable.observeOn(Schedulers.single()).subscribe(item -> {
     *       // этот код выполняется в SingleThreadScheduler
     *   });
     *
     * @param scheduler планировщик для обработки событий
     * @return новый Observable
     */
    public Observable<T> observeOn(Scheduler scheduler) {
        Observable<T> upstream = this;
        return new Observable<>(emitter -> {
            upstream.subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    scheduler.execute(() -> emitter.onNext(item));
                }

                @Override
                public void onError(Throwable t) {
                    scheduler.execute(() -> emitter.onError(t));
                }

                @Override
                public void onComplete() {
                    scheduler.execute(() -> emitter.onComplete());
                }
            });
        });
    }
}
