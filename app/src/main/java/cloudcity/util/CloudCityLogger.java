package cloudcity.util;

import android.util.Log;

import androidx.annotation.NonNull;

import okhttp3.logging.HttpLoggingInterceptor;
import timber.log.Timber;

public class CloudCityLogger {

    public static void d(String logtag, String message, Throwable th) {
        Log.d(logtag, message, th);
        Timber.tag(logtag).d(th, message);
    }

    public static void d(String logtag, String message) {
        d(logtag, message, null);
    }

    public static void v(String logtag, String message, Throwable th) {
        Log.v(logtag, message, th);
        Timber.tag(logtag).v(th, message);
    }

    public static void v(String logtag, String message) {
        v(logtag, message, null);
    }

    public static void i(String logtag, String message, Throwable th) {
        Log.i(logtag, message, th);
        Timber.tag(logtag).i(th, message);
    }

    public static void i(String logtag, String message) {
        i(logtag, message, null);
    }

    public static void w(String logtag, String message, Throwable th) {
        Log.w(logtag, message, th);
        Timber.tag(logtag).w(th, message);
    }

    public static void w(String logtag, String message) {
        w(logtag, message, null);
    }

    public static void e(String logtag, String message, Throwable th) {
        Log.e(logtag, message, th);
        Timber.tag(logtag).e(th, message);
    }

    public static void e(String logtag, String message) {
        e(logtag, message, null);
    }

    public static void wtf(String logtag, String message, Throwable th) {
        Log.wtf(logtag, message, th);
        Timber.tag(logtag).wtf(th, message);
    }

    public static void wtf(String logtag, String message) {
        wtf(logtag, message, null);
    }

    public static class CloudCityLoggerOKHttpLogger implements HttpLoggingInterceptor.Logger {

        @Override
        public void log(@NonNull String s) {
            CloudCityLogger.d("OkHttp", s);
        }
    }
}
