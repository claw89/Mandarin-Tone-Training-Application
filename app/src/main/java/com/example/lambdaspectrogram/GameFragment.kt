package com.example.lambdaspectrogram

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.example.lambdaspectrogram.databinding.FragmentGameBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GameFragment: Fragment() {
    private lateinit var viewModel: GameViewModel

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val binding: FragmentGameBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_game,
            container,
            false
        )

        binding.nextButton.isEnabled = false
        binding.nextButton.isClickable = false

        viewModel = ViewModelProviders.of(this).get(GameViewModel::class.java)

        viewModel.word.observe(this, Observer {newWord ->
            binding.textView.text = newWord.first
        })

        viewModel.eventGameFinish.observe(this, Observer { newEventGameFinished ->
            if (newEventGameFinished) {
                Navigation.findNavController(this.view!!).navigate(
                    GameFragmentDirections.actionGameFragmentToResultFragment(
                        viewModel.score.value!!
                    )
                )
            }
        })

        viewModel.correctTones.observe(this, Observer { newCorrectTones ->
            binding.progressBar1.visibility = View.INVISIBLE
            binding.nextButton.isEnabled = true
            binding.nextButton.isClickable = true
            if (newCorrectTones) {
                binding.resultTextView.text = getString(R.string.correctString)
            }
            else {
                binding.resultTextView.text = getString(R.string.incorrectString)
            }
        })

        binding.nextButton.setOnClickListener {
            binding.resultTextView.text = ""
            viewModel.scorePlusOne()
            viewModel.nextWord()
            binding.nextButton.isEnabled = false
            binding.nextButton.isClickable = false
        }

        binding.button4.setOnClickListener {
            viewModel.playAudio(requireContext())
        }

        binding.recordButton.setOnTouchListener(object: View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> CoroutineScope(Dispatchers.IO).launch {
                        viewModel.startRecording()
                    }
                    MotionEvent.ACTION_UP -> {
                        binding.progressBar1.visibility = View.VISIBLE
                        viewModel.stopRecording()
                        v.performClick()
                    }
                }

                return v.onTouchEvent(event)
            }
        })

        return binding.root
    }
}