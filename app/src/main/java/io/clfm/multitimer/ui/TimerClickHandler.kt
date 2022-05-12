package io.clfm.multitimer.ui

import io.clfm.multitimer.data.Timer

/**
 * Callbacks for user interactions with items in the timer list.
 */
interface TimerClickHandler {

    fun onPlayOrPause(timer: Timer)

    fun onReset(timer: Timer)

    fun onEdit(timer: Timer)

    fun onDelete(timer: Timer)

    fun onReposition(timerViewHolder: TimerListAdapter.TimerViewHolder)

}
