package io.clfm.multitimer.ui

import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemAnimator
import androidx.recyclerview.widget.SimpleItemAnimator
import io.clfm.multitimer.MultiTimerApplication
import io.clfm.multitimer.R
import io.clfm.multitimer.data.Timer
import io.clfm.multitimer.databinding.TimerListFragmentBinding
import kotlin.math.abs
import kotlin.math.min

/**
 * Displays the list of all timers in a single [RecyclerView].
 */
class TimerListFragment : Fragment() {

    private val viewModel: TimerViewModel by activityViewModels {
        TimerViewModelFactory(
            activity?.application!!,
            (activity?.application as MultiTimerApplication).database.timerDao()
        )
    }
    private val timerTouchHelper: ItemTouchHelper by lazy { makeTimerTouchHelper() }

    private var _binding: TimerListFragmentBinding? = null
    private val binding get() = _binding!!
    private var _adapter: TimerListAdapter? = null

    private var isEditingEnabled = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = TimerListFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _adapter = TimerListAdapter(makeTimerClickHandler(), isEditingEnabled)
        binding.recyclerView.adapter = _adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this.context)
        timerTouchHelper.attachToRecyclerView(binding.recyclerView)
        disableChangeAnimations()

        viewModel.allTimers.observe(this.viewLifecycleOwner) { timers ->
            timers.let {
                _adapter?.submitList(it)
                binding.noTimersMessage.visibility =
                    if (timers.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.recyclerView.adapter = _adapter
        viewModel.restoreTimers()
    }

    override fun onStop() {
        super.onStop()
        binding.recyclerView.adapter = null
        viewModel.cleanUpTimers()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val addButton = menu.findItem(R.id.add_menu_button)
        addButton.isVisible = true

        val editButton = menu.findItem(R.id.edit_menu_button)
        updateEditIcon(editButton)

        viewModel.allTimers.observe(this.viewLifecycleOwner) { timers ->
            if (timers.isEmpty()) {
                editButton.isVisible = false
                isEditingEnabled = false
                updateEditIcon(editButton)
            } else {
                editButton.isVisible = true
            }
        }
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add_menu_button -> {
                isEditingEnabled = false
                val action =
                    TimerListFragmentDirections.actionTimerListFragmentToAddOrEditTimerFragment(
                        getString(R.string.add_timer_fragment_title)
                    )
                this.findNavController().navigate(action)
            }
            R.id.edit_menu_button -> {
                isEditingEnabled = !isEditingEnabled
                _adapter?.setIsEditingEnabled(isEditingEnabled)
                updateEditIcon(item)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun startDragging(viewHolder: RecyclerView.ViewHolder) {
        timerTouchHelper.startDrag(viewHolder)
    }

    private fun updateEditIcon(editButton: MenuItem) {
        this.context?.let {
            val editIconId = if (isEditingEnabled) R.drawable.ic_edit_off else R.drawable.ic_edit
            editButton.icon = ContextCompat.getDrawable(it, editIconId)
        }
    }

    /**
     * Suppresses "blinking" or "flashing" animations when list items change.
     */
    private fun disableChangeAnimations() {
        val animator: ItemAnimator? = binding.recyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    private fun makeTimerClickHandler(): TimerClickHandler {
        return object : TimerClickHandler {
            override fun onPlayOrPause(timer: Timer) = viewModel.playOrPauseTimer(timer)

            override fun onReset(timer: Timer) = viewModel.resetTimer(timer)

            override fun onEdit(timer: Timer) {
                isEditingEnabled = false
                val action =
                    TimerListFragmentDirections.actionTimerListFragmentToAddOrEditTimerFragment(
                        getString(R.string.edit_timer_fragment_title),
                        timer.id
                    )
                this@TimerListFragment.findNavController().navigate(action)
            }

            override fun onDelete(timer: Timer) = viewModel.deleteTimer(timer)

            override fun onReposition(timerViewHolder: TimerListAdapter.TimerViewHolder) {
                this@TimerListFragment.startDragging(timerViewHolder)
            }
        }
    }

    /**
     * Handles dragging of timer items to reposition them in the list.
     */
    private fun makeTimerTouchHelper(): ItemTouchHelper {
        val timerTouchCallback = object : ItemTouchHelper.SimpleCallback(
            UP or DOWN or START or END, 0
        ) {
            private val INDETERMINATE = -1 // flag for to-be-determined list positions during drag

            private var originalPosition: Int = INDETERMINATE
            private var newPosition: Int = INDETERMINATE

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition

                if (originalPosition == INDETERMINATE) {
                    originalPosition = from
                }
                newPosition = to // may or may not be final position

                val adapter = recyclerView.adapter as TimerListAdapter
                adapter.notifyItemMoved(from, to)
                return false
            }

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f // make items transparent during drag
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                if (originalPosition != -1 && newPosition != -1 && originalPosition != newPosition) {
                    viewModel.updateTimerListPosition(originalPosition, newPosition)
                }

                val adapter = recyclerView.adapter as? TimerListAdapter
                val start = min(originalPosition, newPosition)
                val itemCount = abs(originalPosition - newPosition)
                adapter?.notifyItemRangeChanged(start, itemCount)

                originalPosition = -1
                newPosition = -1

                viewHolder.itemView.alpha = 1.0f // reset item transparency when drag complete
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // intentionally empty
            }
        }

        return ItemTouchHelper(timerTouchCallback)
    }

}
