package cloudcity.util.ktx

import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.timber.SentryTimberTree
import timber.log.Timber

object TimberUtils {

    @JvmStatic
    fun configTimber() {
        Timber.plant(Timber.DebugTree())
        Timber.plant(SentryTimberTree(
            hub = Sentry.getCurrentHub(),
            minEventLevel = SentryLevel.DEBUG,
            minBreadcrumbLevel = SentryLevel.DEBUG
        ))
    }
}
