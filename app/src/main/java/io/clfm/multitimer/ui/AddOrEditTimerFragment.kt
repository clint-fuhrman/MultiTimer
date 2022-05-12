package io.clfm.multitimer.ui

import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.clfm.multitimer.MultiTimerApplication
import io.clfm.multitimer.R
import io.clfm.multitimer.data.Timer
import io.clfm.multitimer.databinding.FragmentAddOrEditTimerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit

/**
 * Displays components to set a timer's name and initial duration. This fragment allows users to
 * set properties on either a new timer or an existing one, based on [AddOrEditTimerFragmentArgs.timerId].
 */
class AddOrEditTimerFragment : Fragment() {

    private val viewModel: TimerViewModel by activityViewModels {
        TimerViewModelFactory(
            activity?.application!!,
            (activity?.application as MultiTimerApplication).database
                .timerDao()
        )
    }

    private var _binding: FragmentAddOrEditTimerBinding? = null
    private val binding get() = _binding!!

    private val navigationArgs: AddOrEditTimerFragmentArgs by navArgs()
    private lateinit var existingTimer: Timer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = FragmentAddOrEditTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeDurationPickers()

        val timerId = navigationArgs.timerId
        if (timerId > 0) {
            binding.saveAction.setOnClickListener { updateTimer() }
            viewModel.getTimer(timerId).observe(this.viewLifecycleOwner) {
                existingTimer = it
                bindToExistingTimer(it)
            }
        } else {
            binding.saveAction.setOnClickListener { addNewTimer() }
        }

        binding.cancelAction.setOnClickListener { navigateToTimerList() }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Hide the keyboard
        val inputMethodManager =
            requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(requireActivity().currentFocus?.windowToken, 0)

        _binding = null
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val deleteButton = menu.findItem(R.id.delete_menu_button)
        deleteButton.isVisible = navigationArgs.timerId > 0
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.delete_menu_button -> showDeleteConfirmationDialog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun bindToExistingTimer(timer: Timer) {
        binding.apply {
            timerName.setText(timer.name, TextView.BufferType.SPANNABLE)
            hoursPicker.value = TimeUnit.MILLISECONDS.toHours(timer.initialDurationMillis).toInt()
            minutesPicker.value =
                TimeUnit.MILLISECONDS.toMinutes(timer.initialDurationMillis).toInt() % 60
            secondsPicker.value =
                TimeUnit.MILLISECONDS.toSeconds(timer.initialDurationMillis).toInt() % 60
        }
    }

    private fun initializeDurationPickers() {
        binding.apply {
            hoursPicker.minValue = 0
            hoursPicker.maxValue = 23

            minutesPicker.minValue = 0
            minutesPicker.maxValue = 59

            secondsPicker.minValue = 0
            secondsPicker.maxValue = 59
        }
    }

    /**
     * Persists the new timer (if valid), and navigates back to the timer list.
     */
    private fun addNewTimer() {
        if (isTimerInputValid()) {
            viewModel.addNewTimer(
                binding.timerName.text.toString().trim(),
                getInitialDurationMillis()
            )
            navigateToTimerList()
        }
    }

    /**
     * Updates and resets the existing timer (if valid), and navigates back to the timer list.
     */
    private fun updateTimer() {
        if (isTimerInputValid()) {
            viewModel.updateAndResetTimer(
                navigationArgs.timerId,
                binding.timerName.text.toString().trim(),
                getInitialDurationMillis(),
            )
            navigateToTimerList()
        }
    }

    private fun isTimerInputValid(): Boolean {
        return viewModel.isTimerInputValid(
            binding.timerName.text.toString().trim(),
            getInitialDurationMillis()
        )
    }

    private fun getInitialDurationMillis(): Long {
        return 1000 *
                (binding.hoursPicker.value * 60L * 60L +
                        binding.minutesPicker.value * 60L +
                        binding.secondsPicker.value)
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(android.R.string.dialog_alert_title))
            .setMessage(getString(R.string.delete_question, existingTimer.name))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.no)) { _, _ -> }
            .setPositiveButton(getString(R.string.yes)) { _, _ -> deleteTimer() }
            .show()
    }

    private fun deleteTimer() {
        viewModel.deleteTimer(existingTimer)
        navigateToTimerList()
    }

    private fun navigateToTimerList() {
        val action =
            AddOrEditTimerFragmentDirections.actionAddOrEditTimerFragmentToTimerListFragment()
        findNavController().navigate(action)
    }

}
