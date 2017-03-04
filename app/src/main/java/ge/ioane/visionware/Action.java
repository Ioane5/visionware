package ge.ioane.visionware;

/**
 * Created by ioane5 on 3/4/17.
 */

public interface Action<T> {
    void onSuccess(T result);

    void onError();
}
