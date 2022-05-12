package io.clfm.multitimer.data

import androidx.annotation.Nullable
import androidx.room.*
import org.jetbrains.annotations.NotNull
import java.time.Instant

@Entity(tableName = "timer", indices = [Index(value = ["list_position"], unique = true)])
data class Timer(

    @NotNull @PrimaryKey(autoGenerate = true)
    val id: Int = 0, // Room will treat 0 as "not set" for inserts

    @NotNull @ColumnInfo(name = "name")
    val name: String,

    @NotNull @ColumnInfo(name = "initial_duration_millis")
    val initialDurationMillis: Long,

    @NotNull @ColumnInfo(name = "state")
    val state: TimerState,

    /**
     * The [Instant] at which a timer will finish. This field will be non-null only for timers in
     * the [TimerState.RUNNING] state.
     */
    @Nullable @ColumnInfo(name = "finish_time")
    val finishTime: Instant?,

    /**
     * Last known value for milliseconds remaining. For timers in the [TimerState.RUNNING] state,
     * this field may be out-of-date (e.g., if the application was closed). In this case,
     * [finishTime] should be used to determine the true finish time.
     */
    @NotNull @ColumnInfo(name = "millis_until_finished")
    val millisUntilFinished: Long,

    /**
     * Display position of this timer in the list of all timers.
     */
    @NotNull @ColumnInfo(name = "list_position")
    val listPosition: Int = -1

)
