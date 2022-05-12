package io.clfm.multitimer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TimerDao {

    @Query("SELECT * from timer WHERE id = :id")
    abstract suspend fun getTimer(id: Int): Timer

    @Query("SELECT * from timer WHERE id = :id")
    abstract fun getTimerFlow(id: Int): Flow<Timer>

    @Query("SELECT * from timer ORDER BY list_position ASC")
    abstract suspend fun getTimers(): List<Timer>

    @Query("SELECT * from timer ORDER BY list_position ASC")
    abstract fun getTimersFlow(): Flow<List<Timer>>

    /**
     * Inserts the given timer at the end of the list of all timers.
     */
    @Transaction
    open suspend fun insert(timer: Timer) {
        val nextListPosition = getNextListPosition()
        val updatedTimer = timer.copy(listPosition = nextListPosition)
        insertStatement(updatedTimer)
    }

    /**
     * Updates the given timer after checking data integrity constraints.
     */
    @Transaction
    open suspend fun update(timer: Timer) {
        val oldTimer = getTimer(timer.id)
        if (timer.listPosition != oldTimer.listPosition) {
            throw TimerDaoException("Timer list position must be updated using the dedicated method.")
        }
        updateStatement(timer)
    }

    @Query("UPDATE Timer SET millis_until_finished = :millisUntilFinished WHERE id = :timerId")
    abstract suspend fun updateMillisUntilFinished(timerId: Int, millisUntilFinished: Long)

    /**
     * Moves the timer with [Timer.listPosition] "from" to position "to", and updates
     * the list position of any other timers affected by this move.
     */
    @Transaction
    open suspend fun updateTimerListPosition(from: Int, to: Int) {
        if (from == to) {
            return
        }

        val maxPosition = getNextListPosition() - 1
        if (from !in 0..maxPosition || to !in 0..maxPosition) {
            throw TimerDaoException(
                "Invalid list position update arguments. " +
                        "Attempted to move timer from index $from to index $to " +
                        "(max list position is currently ${maxPosition})."
            )
        }

        val timerToMove = getTimerAtPosition(from)
        updateListPosition(timerToMove.id, -1) // temporary position (due to unique index)
        if (to < from) {
            // timer moved upwards in the list; shift any timers between 'from' and 'to' downwards
            val timersToMoveDown = getTimersBetweenPositions(to, from - 1)
            for (timer in timersToMoveDown.reversed()) {
                updateListPosition(timer.id, timer.listPosition + 1)
            }
        } else {
            // timer moved downwards in the list; shift any timers between 'from' and 'to' upwards
            val timersToMoveUp = getTimersBetweenPositions(from + 1, to)
            for (timer in timersToMoveUp) {
                updateListPosition(timer.id, timer.listPosition - 1)
            }
        }
        updateListPosition(timerToMove.id, to)
    }

    @Transaction
    open suspend fun delete(timer: Timer) {
        val maxPosition = getNextListPosition() - 1

        deleteStatement(timer)

        // move any timers that were below the deleted timer upwards in the list
        val timersToMoveUp = getTimersBetweenPositions(timer.listPosition + 1, maxPosition)
        for (timerToMove in timersToMoveUp) {
            updateListPosition(timerToMove.id, timerToMove.listPosition - 1)
        }
    }

    /**
     * Returns the index of the next available list position in the list of all timers.
     * Since N timers always fill list positions 0 through N-1, this query simply returns N.
     */
    @Query("SELECT COUNT(*) from timer")
    protected abstract suspend fun getNextListPosition(): Int

    @Query("SELECT * from timer WHERE list_position = :listPosition")
    protected abstract suspend fun getTimerAtPosition(listPosition: Int): Timer

    @Query("UPDATE Timer SET list_position = :listPosition WHERE id = :timerId")
    protected abstract suspend fun updateListPosition(timerId: Int, listPosition: Int)

    @Query("SELECT * from timer WHERE list_position BETWEEN :start AND :end")
    protected abstract suspend fun getTimersBetweenPositions(start: Int, end: Int): List<Timer>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertStatement(timer: Timer)

    @Update
    protected abstract suspend fun updateStatement(timer: Timer)

    @Delete
    protected abstract suspend fun deleteStatement(timer: Timer)

}
