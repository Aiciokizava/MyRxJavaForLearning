package rx;

/**
 * Функциональный интерфейс — источник данных.
 * Передаётся в {@link Observable # create(ObservableOnSubscribe)}.
 *
 * @param <T> тип элементов
 */
@FunctionalInterface
public interface ObservableOnSubscribe<T> {

    /**
     * Вызывается при подписке. Источник должен отправлять данные через emitter.
     *
     * @param emitter эмиттер для отправки событий
     */
    void subscribe(ObservableEmitter<T> emitter);
}
