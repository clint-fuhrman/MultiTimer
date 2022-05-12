package io.clfm.multitimer.data.converter

import androidx.room.TypeConverter
import io.clfm.multitimer.data.TimerState

/**
 * Encapsulates [TypeConverter] methods for converting a [TimerState] to or from its database representation.
 */
class TimerStateConverter {

    @TypeConverter
    fun fromTimerState(timerState: TimerState): String {
        return timerState.name
    }

    @TypeConverter
    fun toTimerState(timerState: String): TimerState {
        return TimerState.valueOf(timerState)
    }

}
