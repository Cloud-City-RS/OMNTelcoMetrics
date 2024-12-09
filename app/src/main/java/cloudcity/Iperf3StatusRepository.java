package cloudcity;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

public class Iperf3StatusRepository {

    private static volatile Iperf3StatusRepository instance;
    private volatile AtomicBoolean runningStatus;

    private Iperf3StatusRepository() {
        runningStatus = new AtomicBoolean(false);
    }

    /**
     * Initializes the repository, loads saved data and prefills it with data if no data was found
     *
     * @param ctx the context to use
     */
    public static void initialize() {
        if (instance != null) {
            throw new IllegalStateException("Already initialized!");
        } else {
            // Since instance is null, this branch is already the first part of
            // the double-locked synchronized singleton creation
            synchronized (Iperf3StatusRepository.class) {
                if (instance == null) {
                    instance = new Iperf3StatusRepository();
                }
            }
        }
    }

    public static Iperf3StatusRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Must call initialize() first!");
        }

        return instance;
    }

    /**
     * Sets runningStatus to true if it's currently false and returns true if that was the case. Throws otherwise
     * @return true of throws if expected value (false) didn't match the current value
     */
    public boolean startRunning() {
        boolean actualStatus = runningStatus.compareAndSet(false, true);
        if (actualStatus) {
            return true;
        } else {
            throw new IllegalStateException("Actually failed to compareAndSet runningStatus! actualValue (" + actualStatus + ") was not equal to (false) before setting to (true)!");
        }
    }

    /**
     * Sets runningStatus to false if it's currently true and returns true if that was the case. Throws otherwise
     * @return true or throws if expected value (true) didn't match the current value
     */
    public boolean stopRunning() {
        boolean actualStatus = runningStatus.compareAndSet(true, false);
        if (actualStatus) {
            return true;
        } else {
            throw new IllegalStateException("Actually failed to compareAndSet runningStatus! actualValue (" + actualStatus + ") was not equal to (true) before setting to (false)!");
        }
    }

    /**
     * Returns the current running status
     * @return the current state of runningStatus
     */
    public synchronized boolean getRunningStatus() {
        return runningStatus.get();
    }
}
