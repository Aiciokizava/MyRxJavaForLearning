package rx;

/**
 * Эмиттер — объект, через который источник данных отправляет события наблюдателю.
 * Оборачивает Observer и добавляет поддержку Disposable (отмены подписки).
 *
 * @param <T> тип элементов
 */
public class ObservableEmitter<T> implements Disposable {

    private final Observer<T> observer;
    private volatile boolean disposed = false;
    private volatile boolean done = false;

    public ObservableEmitter(Observer<T> observer) {
        this.observer = observer;
    }

    /**
     * Отправляет элемент наблюдателю, если подписка активна и поток не завершён.
     */
    public void onNext(T item) {
        if (!disposed && !done) {
            try {
                observer.onNext(item);
            } catch (Throwable t) {
                onError(t);
            }
        }
    }

    /**
     * Сигнализирует об ошибке. Терминальное событие — после него поток завершён.
     */
    public void onError(Throwable t) {
        if (!disposed && !done) {
            done = true;
            observer.onError(t);
        }
    }

    /**
     * Сигнализирует о нормальном завершении потока. Терминальное событие.
     */
    public void onComplete() {
        if (!disposed && !done) {
            done = true;
            observer.onComplete();
        }
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
