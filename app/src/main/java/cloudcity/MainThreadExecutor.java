package cloudcity;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * A poor-man's {@link Executor} that's actually a {@link Handler} backed by {@link Looper#getMainLooper()}
 */
public class MainThreadExecutor implements Executor {
    private static volatile MainThreadExecutor instance;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(@NonNull Runnable command) {
        handler.post(command);
    }

    public static synchronized MainThreadExecutor getInstance() {
        if (instance == null) {
            synchronized (MainThreadExecutor.class) {
                if (instance == null) {
                    instance = new MainThreadExecutor();
                }
            }
        }

        return instance;
    }

    private MainThreadExecutor() {
        // Private constructor to disable instantiation
    }
}
