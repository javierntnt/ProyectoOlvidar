package com.remindme.domain.use_case

/**
 * Quiet-hours check (spec: Quiet Hours — "REJECTED: posted during quiet hours").
 *
 * Minutes are 0..1439. A window where start <= end spans start (inclusive) to
 * end (exclusive) within one day; a window where start > end wraps past
 * midnight (e.g. 23:00..07:00).
 */
object QuietHours {

    fun isInsideQuietWindow(nowMinuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        require(startMinute in 0..1439) { "quietStartMinute must be 0..1439, was $startMinute" }
        require(endMinute in 0..1439) { "quietEndMinute must be 0..1439, was $endMinute" }
        return if (startMinute <= endMinute) {
            nowMinuteOfDay in startMinute until endMinute
        } else {
            nowMinuteOfDay >= startMinute || nowMinuteOfDay < endMinute
        }
    }
}