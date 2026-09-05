package com.remindme

/**
 * Application subclass used only by Robolectric tests. Overrides the
 * WorkManager bootstrap so the WorkManager TestDriver can be installed
 * in `@Before` without an "already initialized" conflict.
 */
class TestRemindMeApp : RemindMeApp() {

    override fun initializeWorkManager() {
        // No-op under tests: leaves WorkManager uninitialized on purpose.
    }
}