package com.shd.codestudio.analytics.impl

import com.shd.codestudio.analytics.AnalyticsEvent
import com.shd.codestudio.analytics.AnalyticsService
import com.shd.codestudio.analytics.CrashScrub
import com.shd.codestudio.analytics.EventCategory
import com.shd.codestudio.analytics.Events
import com.shd.codestudio.platform.log.LogLevel
import com.shd.codestudio.platform.log.LogRecord
import com.shd.codestudio.platform.log.LogSink
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges the logging facade to analytics: every `ERROR` record that carries a throwable is forwarded as a
 * scrubbed [Events.ERROR_LOGGED] event (CRASH category) — the type chain + our own frames, no messages, no
 * paths (via [CrashScrub.scrub]). The log record's own message is intentionally dropped, since a
 * developer-written message could embed a path or user content.
 *
 * Throttled per exception type ([throttleMs]) so a tight failing loop can't flood the queue/free tier. The
 * service itself no-ops when consent is absent, so this is harmless when analytics is off.
 */
class AnalyticsLogSink(
    private val analytics: AnalyticsService,
    private val throttleMs: Long = 10_000,
) : LogSink {

    private val lastSent = ConcurrentHashMap<String, Long>()

    override fun log(record: LogRecord) {
        val t = record.throwable ?: return
        if (record.level != LogLevel.ERROR) return

        val signature = t.javaClass.name
        val now = System.currentTimeMillis()
        val prev = lastSent[signature]
        if (prev != null && now - prev < throttleMs) return // suppress bursts of the same error
        lastSent[signature] = now

        val props = HashMap(CrashScrub.scrub(t))
        props["tag"] = record.tag
        analytics.track(AnalyticsEvent(Events.ERROR_LOGGED, EventCategory.CRASH, props))
    }
}
