# My RxJava — реализация реактивной библиотеки

## Описание

Реализация основных концепций библиотеки RxJava с нуля.
Включает базовые компоненты реактивного потока, операторы преобразования данных,
управление потоками выполнения (Schedulers) и механизм отмены подписки.

## Структура проекта

```
my-rxjava/
├── src/
│   ├── main/java/rx/
│   │   ├── Observable.java
│   │   ├── Observer.java
│   │   ├── Disposable.java
│   │   ├── ObservableOnSubscribe.java
│   │   ├── ObservableEmitter.java
│   │   ├── Schedulers.java
│   │   ├── Demo.java
│   │   └── scheduler/
│   │       ├── Scheduler.java
│   │       ├── IOThreadScheduler.java
│   │       ├── ComputationScheduler.java
│   │       └── SingleThreadScheduler.java
│   └── test/java/rx/
│       └── ObservableTest.java
├── pom.xml
└── README.md
```

## Сборка и запуск

Требования: Java 17+, Maven 3.8+

```bash
# Сборка
mvn compile

# Запуск тестов
mvn test

# Запуск демо
mvn compile exec:java -Dexec.mainClass="rx.Demo"
```
---

## Архитектура системы

### Общая схема

```
Observable.create(source)
        │
        ▼
  ┌─────────────┐
  │  Operators  │  map -> filter -> flatMap
  └──────┬──────┘
         │
    ┌────┴─────┐
    ▼          ▼
subscribeOn  observeOn
(поток       (поток
подписки)    обработки)
    │          │
    └────┬─────┘
         ▼
   ┌───────────┐
   │  Observer  │
   │  onNext()  │
   │  onError() │
   │ onComplete │
   └───────────┘
```

### Ключевые классы

| Класс / Интерфейс      | Назначение                                                     |
|-------------------------|----------------------------------------------------------------|
| `Observer<T>`           | Наблюдатель — получает элементы, ошибки и сигнал завершения    |
| `Observable<T>`         | Реактивный поток — источник данных с операторами преобразования |
| `ObservableEmitter<T>`  | Мост между источником и наблюдателем, поддерживает Disposable  |
| `ObservableOnSubscribe` | Функциональный интерфейс — логика источника данных             |
| `Disposable`            | Управление подпиской (отмена)                                  |
| `Scheduler`             | Абстракция над пулом потоков                                   |

### Принцип работы

1. **Создание.** `Observable.create(source)` сохраняет ссылку на источник данных. Никакой работы не происходит до момента подписки (ленивое выполнение).

2. **Цепочка операторов.** Каждый оператор (`map`, `filter`, `flatMap`) создаёт новый Observable, который при подписке подписывается на предыдущий (upstream) и трансформирует события.

3. **Подписка.** Вызов `subscribe(observer)` запускает всю цепочку — от последнего оператора к первому. Источник начинает эмитить данные.

4. **Терминальные события.** После `onError()` или `onComplete()` эмиттер блокирует дальнейшую отправку событий.

---

## Schedulers: принципы работы и различия

### Общий интерфейс

```java
public interface Scheduler {
    void execute(Runnable task);
    void shutdown();
}
```

Каждый Scheduler оборачивает определённый тип пула потоков из `java.util.concurrent`.

### Три реализации

| Scheduler               | Пул потоков              | Когда использовать                     |
|-------------------------|--------------------------|----------------------------------------|
| `IOThreadScheduler`     | CachedThreadPool         | I/O-операции: сеть, файлы, БД         |
| `ComputationScheduler`  | FixedThreadPool (N ядер) | CPU-bound задачи: вычисления, парсинг  |
| `SingleThreadScheduler` | SingleThreadExecutor     | Последовательные задачи, обновление UI |

**IOThreadScheduler** — создаёт новые потоки по мере необходимости и переиспользует освободившиеся. Количество потоков не ограничено. Подходит для задач, которые большую часть времени ожидают (блокируются на I/O).

**ComputationScheduler** — фиксированный пул, количество потоков равно числу доступных процессоров. Подходит для задач, которые активно используют CPU. Ограниченный размер пула предотвращает избыточное переключение контекста.

**SingleThreadScheduler** — один поток. Гарантирует строго последовательное выполнение задач. Подходит для работы с ресурсами, не допускающими конкурентный доступ.

### subscribeOn vs observeOn

**`subscribeOn(scheduler)`** — определяет, в каком потоке выполняется источник данных (метод `subscribe` у `ObservableOnSubscribe`). Влияет на весь upstream. Не важно, где в цепочке вызван — всегда влияет на источник.

**`observeOn(scheduler)`** — определяет, в каком потоке наблюдатель получает события (`onNext`, `onError`, `onComplete`). Влияет на downstream — всё, что идёт после него в цепочке.

```
subscribeOn(io)          observeOn(single)
      │                        │
      ▼                        ▼
[IO Thread]              [Single Thread]
  source                   observer
  emits --> map --> filter --> onNext()
```

---

## Операторы

### map(Function<T, R> mapper)

Преобразует каждый элемент потока. Если `mapper` выбрасывает исключение, оно перехватывается и передаётся в `onError()`.

```java
observable
    .map(x -> x * 2)
    .subscribe(System.out::println);
// Вход: 1, 2, 3
// Выход: 2, 4, 6
```

### filter(Predicate<T> predicate)

Пропускает только элементы, удовлетворяющие условию. Сигнал `onComplete()` пробрасывается всегда.

```java
observable
    .filter(x -> x % 2 == 0)
    .subscribe(System.out::println);
// Вход: 1, 2, 3, 4, 5
// Выход: 2, 4
```

### flatMap(Function<T, Observable<R>> mapper)

Для каждого элемента создаёт внутренний Observable и разворачивает его элементы в общий поток. Завершение внутреннего Observable не завершает внешний.

```java
observable
    .flatMap(word -> Observable.<String>create(e -> {
        for (char c : word.toCharArray()) e.onNext(String.valueOf(c));
        e.onComplete();
    }))
    .subscribe(System.out::println);
// Вход: "Hi", "RxJava"
// Выход: H, i, R, x, J, a, v, a
```

---

## Disposable

`ObservableEmitter` реализует интерфейс `Disposable`. При вызове `dispose()`:

- Устанавливается флаг `disposed = true`
- Все последующие вызовы `onNext`, `onError`, `onComplete` игнорируются
- Источник может проверять `emitter.isDisposed()` и прекращать работу досрочно

```java
Disposable d = observable.subscribe(item -> System.out.println(item));
d.dispose(); // подписка отменена
d.isDisposed(); // true
```

---

## Обработка ошибок

Ошибки обрабатываются на трёх уровнях:

1. **В источнике.** Исключение, выброшенное в `source.subscribe()`, перехватывается методом `Observable.subscribe()` и передаётся в `onError()`.

2. **В операторах.** Исключение в `mapper.apply()` или `predicate.test()` перехватывается внутри оператора и передаётся в `onError()`.

3. **В наблюдателе.** Исключение в `observer.onNext()` перехватывается эмиттером и передаётся в `onError()`.

После `onError()` поток считается завершённым — флаг `done = true` предотвращает дальнейшую отправку событий.

---

## Тестирование

### Запуск

```bash
mvn test
```

### Результат

```
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Покрытые сценарии

| №  | Тест                            | Что проверяет                                |
|----|---------------------------------|----------------------------------------------|
| 1  | testCreateAndSubscribe          | Базовая доставка элементов и onComplete      |
| 2  | testEmptyStream                 | Пустой поток                                 |
| 3  | testNoEventsAfterComplete       | Игнорирование событий после onComplete       |
| 4  | testMap                         | Оператор map                                 |
| 5  | testMapError                    | Ошибка в map пробрасывается в onError        |
| 6  | testFilter                      | Оператор filter                              |
| 7  | testFilterNonePass              | Filter — ни один элемент не проходит         |
| 8  | testMapThenFilter               | Цепочка map + filter                         |
| 9  | testFlatMap                     | Оператор flatMap                             |
| 10 | testFlatMapInnerError           | Ошибка во внутреннем Observable              |
| 11 | testDisposable                  | Отмена подписки                              |
| 12 | testDisposeInsideOnNext         | Отмена подписки изнутри onNext               |
| 13 | testErrorInSource               | Ошибка в источнике                           |
| 14 | testNoEventsAfterError          | Игнорирование событий после onError          |
| 15 | testSubscribeOn                 | Подписка в другом потоке                     |
| 16 | testObserveOn                   | Обработка в другом потоке                    |
| 17 | testSubscribeOnAndObserveOn     | Подписка и обработка в разных потоках        |
| 18 | testComputationScheduler        | ComputationScheduler работает                |
| 19 | testFullChain                   | Интеграционный тест всей цепочки             |

---

## Примеры использования

### Простой поток

```java
Observable.<String>create(emitter -> {
    emitter.onNext("Hello");
    emitter.onNext("RxJava");
    emitter.onComplete();
}).subscribe(item -> System.out.println("Received: " + item));
```

Вывод:
```
Received: Hello
Received: RxJava
```

### Цепочка операторов

```java
Observable.<Integer>create(emitter -> {
    for (int i = 1; i <= 20; i++) {
        emitter.onNext(i);
    }
    emitter.onComplete();
})
.filter(x -> x % 2 == 0)
.map(x -> x * x)
.subscribe(new Observer<Integer>() {
    public void onNext(Integer item) { System.out.println(item); }
    public void onError(Throwable t) { t.printStackTrace(); }
    public void onComplete() { System.out.println("Done!"); }
});
```

Вывод:
```
4
16
36
64
100
144
196
256
324
400
Done!
```

### Асинхронное выполнение

```java
Observable.<Integer>create(emitter -> {
    System.out.println("Source: " + Thread.currentThread().getName());
    emitter.onNext(42);
    emitter.onComplete();
})
.subscribeOn(Schedulers.io())
.observeOn(Schedulers.single())
.map(x -> "value=" + x)
.subscribe(new Observer<String>() {
    public void onNext(String item) {
        System.out.println("Observer: " + Thread.currentThread().getName());
        System.out.println(item);
    }
    public void onError(Throwable t) { t.printStackTrace(); }
    public void onComplete() { System.out.println("Done!"); }
});

Thread.sleep(1000);
```

Вывод:
```
Source: RxIO-21
Observer: RxSingle-22
value=42
Done!
```

Три разных потока (main, RxIO, RxSingle) подтверждают корректную работу Schedulers.
