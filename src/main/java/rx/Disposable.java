package rx;

/**
 * Управление подпиской. Позволяет отменить подписку,
 * после чего наблюдатель перестаёт получать события.
 */
public interface Disposable {

    /**
     * Отменяет подписку.
     */
    void dispose();

    /**
     * @return true, если подписка уже отменена
     */
    boolean isDisposed();
}
