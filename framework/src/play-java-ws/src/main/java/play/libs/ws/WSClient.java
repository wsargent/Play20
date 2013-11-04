package play.libs.ws;

/**
 *
 * @param <T>
 */
public interface WSClient<T> {

    public T getUnderlying();
}