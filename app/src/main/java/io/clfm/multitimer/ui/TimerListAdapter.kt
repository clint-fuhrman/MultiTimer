package io.clfm.multitimer.ui

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.clfm.multitimer.R
import io.clfm.multitimer.data.Timer
import io.clfm.multitimer.data.TimerState
import io.clfm.multitimer.databinding.TimerListItemBinding
import com.google.android.material.color.MaterialColors
import org.apache.commons.lang3.time.DurationFormatUtils

/**
 * [ListAdapter] for [RecyclerView] containing the list of all timers.
 */
class TimerListAdapter(
    private val timerClickHandler: TimerClickHandler,
    private var isEditingEnabled: Boolean
) : ListAdapter<Timer, TimerListAdapter.TimerViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        return TimerViewHolder(
            TimerListItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = getItem(position)
        holder.bind(timer, timerClickHandler, isEditingEnabled)
    }

    fun setIsEditingEnabled(isEditingEnabled: Boolean) {
        this.isEditingEnabled = isEditingEnabled
        this.notifyItemRangeChanged(0, this.itemCount)
    }

    class TimerViewHolder(private val binding: TimerListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            timer: Timer,
            timerClickHandler: TimerClickHandler,
            isEditingEnabled: Boolean
        ) {
            binding.apply {
                timerCard.setCardBackgroundColor(getTimerBackgroundColor(timer))

                timerName.text = timer.name
                timerTimeRemaining.text = getFormattedDuration(timer)

                resetButton.setImageResource(getResetImageId(timer))
                resetButton.setOnClickListener { timerClickHandler.onReset(timer) }

                playOrPauseButton.setImageResource(getPlayOrPauseImageId(timer))
                playOrPauseButton.contentDescription = getPlayOrPauseContentDescription(timer)
                playOrPauseButton.setOnClickListener { timerClickHandler.onPlayOrPause(timer) }

                bindProgressIndicator(timer)

                configureEditComponents(isEditingEnabled, timer, timerClickHandler)
            }
        }

        private fun getTimerBackgroundColor(timer: Timer): Int {
            val cardBackgroundColor = when (timer.state) {
                TimerState.NOT_STARTED -> R.attr.notStartedTimerColor
                TimerState.RUNNING -> R.attr.runningTimerColor
                TimerState.PAUSED -> R.attr.pausedTimerColor
                TimerState.FINISHED -> R.attr.finishedTimerColor
            }
            return MaterialColors.getColor(this.itemView, cardBackgroundColor)
        }

        private fun getFormattedDuration(timer: Timer): String =
            DurationFormatUtils.formatDuration(timer.millisUntilFinished, "H:mm:ss", true)

        private fun getResetImageId(timer: Timer): Int {
            return when (timer.state) {
                TimerState.PAUSED, TimerState.FINISHED -> R.drawable.ic_reset
                else -> 0
            }
        }

        private fun getPlayOrPauseImageId(timer: Timer): Int {
            return when (timer.state) {
                TimerState.RUNNING -> R.drawable.ic_pause
                TimerState.NOT_STARTED -> R.drawable.ic_play
                TimerState.PAUSED -> R.drawable.ic_play_outline
                TimerState.FINISHED -> 0
            }
        }

        private fun getPlayOrPauseContentDescription(timer: Timer): String {
            val stringId = when (timer.state) {
                TimerState.RUNNING -> R.string.pause_button_description
                TimerState.NOT_STARTED, TimerState.PAUSED -> R.string.play_button_description
                TimerState.FINISHED -> null
            }
            return stringId?.let { binding.timerCard.context.getString(it) } ?: ""
        }

        private fun bindProgressIndicator(timer: Timer) {
            if (timer.state == TimerState.NOT_STARTED) {
                binding.progressIndicator.visibility = View.GONE
            } else {
                binding.progressIndicator.visibility = View.VISIBLE
                binding.progressIndicator.max = 1000 // smoother animations than the default (100)
                val progress = (timer.millisUntilFinished.toDouble() / timer.initialDurationMillis
                        * binding.progressIndicator.max).toInt()
                binding.progressIndicator.setProgress(progress, true)
            }
        }

        private fun configureEditComponents(
            isEditingEnabled: Boolean,
            timer: Timer,
            timerClickHandler: TimerClickHandler
        ) = binding.apply {
            if (isEditingEnabled) {
                this@TimerViewHolder.itemView.setOnClickListener { timerClickHandler.onEdit(timer) }

                deleteButton.visibility = View.VISIBLE
                deleteButton.setOnClickListener { timerClickHandler.onDelete(timer) }

                dragHandle.visibility = View.VISIBLE
                dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        timerClickHandler.onReposition(this@TimerViewHolder)
                    }
                    return@setOnTouchListener true
                }

                playOrPauseButton.isEnabled = false
                resetButton.isEnabled = false
            } else {
                this@TimerViewHolder.itemView.setOnClickListener {}

                deleteButton.visibility = View.GONE
                dragHandle.visibility = View.GONE

                playOrPauseButton.isEnabled = true
                resetButton.isEnabled = true
            }
        }

    }

    private companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Timer>() {
            override fun areItemsTheSame(oldTimer: Timer, newTimer: Timer): Boolean {
                return oldTimer.id == newTimer.id
            }

            override fun areContentsTheSame(oldTimer: Timer, newTimer: Timer): Boolean {
                return oldTimer.name == newTimer.name
                        && oldTimer.initialDurationMillis == newTimer.initialDurationMillis
                        && oldTimer.state == newTimer.state
                        && oldTimer.millisUntilFinished == newTimer.millisUntilFinished
                        && oldTimer.listPosition == newTimer.listPosition
            }
        }
    }

}
