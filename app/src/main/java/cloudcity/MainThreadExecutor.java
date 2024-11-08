package cloudcity;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * A poor-man's {@link Executor} that's actually a {@link Handler} backed by {@link Looper#getMainLooper()}
 */
public class MainThreadExecutor implements Executor {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(@NonNull Runnable command) {
        handler.post(command);
    }
}
