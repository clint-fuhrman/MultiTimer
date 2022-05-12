package io.clfm.multitimer.ui

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.SystemClock
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import io.clfm.multitimer.data.Timer
import io.clfm.multitimer.data.TimerDao
import io.clfm.multitimer.data.TimerState
import io.clfm.multitimer.notification.AlarmReceiver
import io.clfm.multitimer.notification.IntentExtraKeys
import io.clfm.multitimer.notification.cancelNotification
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

/**
 * [ViewModel] for [Timer] list. Serves as a repository for timer state, both persisted
 * (e.g., timer properties saved to the database) and transient (e.g., in-memory countdowns
 * for running timers).
 */
class TimerViewModel(private val app: Application, private val timerDao: TimerDao) :
    AndroidViewModel(app) {

    val allTimers: LiveData<List<Timer>> = timerDao.getTimersFlow().asLiveData()

    private val countDownsByTimerId: ConcurrentHashMap<Int, CountDownTimer> = ConcurrentHashMap()
    private val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private companion object {
        const val MIN_DURATION_MILLIS = 1000
        const val MAX_DURATION_MILLIS = ((24 * 60 * 60) - 1) * 1000 // 23:59:59.000
        const val MIN_NAME_LENGTH = 1
        const val MAX_NAME_LENGTH = 32
    }

    fun getTimer(id: Int): LiveData<Timer> {
        return timerDao.getTimerFlow(id).asLiveData()
    }

    fun addNewTimer(name: String, initialDurationMillis: Long) {
        viewModelScope.launch {
            val newTimer = Timer(
                name = name,
                initialDurationMillis = initialDurationMillis,
                state = TimerState.NOT_STARTED,
                finishTime = null,
                millisUntilFinished = initialDurationMillis
            )
            timerDao.insert(newTimer)
        }
    }

    fun updateAndResetTimer(id: Int, name: String, initialDurationMillis: Long) {
        viewModelScope.launch {
            val oldTimer = timerDao.getTimer(id)
            var newTimer = oldTimer.copy(name = name, initialDurationMillis = initialDurationMillis)
            newTimer = makeTimerWithUpdatedState(newTimer, TimerState.NOT_STARTED)
            timerDao.update(newTimer)
        }
    }

    fun deleteTimer(timer: Timer) {
        clearCountDown(timer.id)
        viewModelScope.launch {
            timerDao.delete(timer)
        }
    }

    fun playOrPauseTimer(timer: Timer) {
        when (timer.state) {
            TimerState.FINISHED -> return // finished timer should be reset, never played/paused
            TimerState.NOT_STARTED, TimerState.PAUSED -> startTimer(timer)
            else -> pauseTimer(timer)
        }
    }

    fun resetTimer(timer: Timer) {
        val updatedTimer = makeTimerWithUpdatedState(timer, TimerState.NOT_STARTED)
        updateTimer(updatedTimer)
    }

    /**
     * Moves the timer with [Timer.listPosition] [from] to position [to], and updates
     * the list position of any other timers affected by this move.
     */
    fun updateTimerListPosition(from: Int, to: Int) =
        viewModelScope.launch { timerDao.updateTimerListPosition(from, to) }

    fun isTimerInputValid(name: String, initialDurationMillis: Long): Boolean {
        val isNameValid = name.trim().length in MIN_NAME_LENGTH..MAX_NAME_LENGTH
        val isDurationValid = initialDurationMillis in MIN_DURATION_MILLIS..MAX_DURATION_MILLIS
        return isNameValid && isDurationValid
    }

    /**
     * Cleans up transient timer state to conserve system resources.
     *
     * Invoke when timer countdowns no longer need to be displayed (e.g., application shutdown
     * or navigation away from [TimerListFragment]).
     */
    fun cleanUpTimers() {
        countDownsByTimerId.values.forEach { it.cancel() }
        countDownsByTimerId.clear()
    }

    /**
     * Updates any timers that are currently in the [TimerState.RUNNING] state.
     *
     * Invoke when timers previously cleaned up with [cleanUpTimers] need to be displayed
     * (e.g., application startup or navigation to [TimerListFragment]).
     */
    fun restoreTimers() {
        viewModelScope.launch {
            timerDao.getTimers()
                .filter { it.state == TimerState.RUNNING }
                .forEach { restoreRunningTimer(it) }
        }
    }

    private fun restoreRunningTimer(timer: Timer) {
        if (timer.state != TimerState.RUNNING) {
            return
        }

        val now = Instant.now()
        val isTimerFinished: Boolean = timer.finishTime?.isBefore(now) ?: true

        val restoredTimer: Timer = if (isTimerFinished) {
            makeTimerWithUpdatedState(timer, TimerState.FINISHED)
        } else {
            val newMillisUntilFinished =
                timer.finishTime?.minusMillis(now.toEpochMilli())?.toEpochMilli()
                    ?: timer.initialDurationMillis
            timer.copy(millisUntilFinished = newMillisUntilFinished)
        }

        if (!isTimerFinished) {
            startCountDown(restoredTimer)
        }

        updateTimer(restoredTimer)
    }

    private fun startTimer(timer: Timer) {
        val runningTimer = makeTimerWithUpdatedState(timer, TimerState.RUNNING)
        updateTimer(runningTimer)
        startCountDown(runningTimer)
    }

    private fun pauseTimer(timer: Timer) {
        val pausedTimer = makeTimerWithUpdatedState(timer, TimerState.PAUSED)
        updateTimer(pausedTimer)
        clearCountDown(timer.id)
        cancelScheduledNotification(timer)
    }

    private fun finishTimer(timer: Timer) {
        val finishedTimer = makeTimerWithUpdatedState(timer, TimerState.FINISHED)
        updateTimer(finishedTimer)
        clearCountDown(timer.id)
    }

    private fun updateTimer(timer: Timer) {
        viewModelScope.launch {
            timerDao.update(timer)
        }
    }

    private fun startCountDown(timer: Timer) {
        clearCountDown(timer.id)

        val countDownTimer = object : CountDownTimer(timer.millisUntilFinished, 500) {
            override fun onTick(millisUntilFinished: Long) {
                viewModelScope.launch {
                    timerDao.updateMillisUntilFinished(timer.id, millisUntilFinished)
                }
            }

            override fun onFinish() = finishTimer(timer)
        }

        countDownsByTimerId[timer.id] = countDownTimer
        countDownTimer.start()

        scheduleTimerFinishedNotification(timer)
    }

    private fun clearCountDown(timerId: Int) {
        countDownsByTimerId[timerId]?.cancel()
        countDownsByTimerId.remove(timerId)
    }

    /**
     * Returns a copy of the given [Timer] with [Timer.state] set to the given [TimerState],
     * and with [Timer.millisUntilFinished] and [Timer.finishTime] updated accordingly.
     */
    private fun makeTimerWithUpdatedState(timer: Timer, newState: TimerState): Timer {
        return when (newState) {
            TimerState.FINISHED -> {
                timer.copy(
                    state = newState,
                    millisUntilFinished = 0,
                    finishTime = null
                )
            }
            TimerState.PAUSED -> {
                timer.copy(state = newState, finishTime = null)
            }
            TimerState.NOT_STARTED -> {
                timer.copy(
                    state = newState,
                    millisUntilFinished = timer.initialDurationMillis,
                    finishTime = null
                )
            }
            TimerState.RUNNING -> {
                val finishTime = Instant.now().plusMillis(timer.millisUntilFinished)
                timer.copy(state = newState, finishTime = finishTime)
            }
        }
    }

    private fun scheduleTimerFinishedNotification(timer: Timer) {
        val triggerAtMillis = SystemClock.elapsedRealtime() + timer.millisUntilFinished

        // Cancel any pending notifications for this timer
        val notificationManager = ContextCompat.getSystemService(
            app,
            NotificationManager::class.java
        ) as NotificationManager
        notificationManager.cancelNotification(timer.id)

        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtMillis,
            createNotificationPendingIntent(timer)
        )
    }

    private fun createNotificationPendingIntent(timer: Timer): PendingIntent {
        val notificationIntent = Intent(app, AlarmReceiver::class.java)
            .putExtra(IntentExtraKeys.TIMER_ID, timer.id)
            .putExtra(IntentExtraKeys.TIMER_NAME, timer.name)

        return PendingIntent.getBroadcast(
            getApplication(),
            timer.id,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun cancelScheduledNotification(timer: Timer) {
        alarmManager.cancel(createNotificationPendingIntent(timer))
    }

}

class TimerViewModelFactory(private val app: Application, private val timerDao: TimerDao) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimerViewModel(app, timerDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
