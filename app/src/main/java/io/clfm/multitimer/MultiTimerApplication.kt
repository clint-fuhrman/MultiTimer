package io.clfm.multitimer

import android.app.Application
import io.clfm.multitimer.data.TimerDatabase

class MultiTimerApplication : Application() {
    val database: TimerDatabase by lazy { TimerDatabase.getDatabase(this) }
}
