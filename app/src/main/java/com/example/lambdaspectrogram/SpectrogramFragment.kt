package com.example.lambdaspectrogram

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.example.lambdaspectrogram.databinding.FragmentSpectrogramBinding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SpectrogramFragment: Fragment() {
    private lateinit var viewModel: SpectrogramViewModel

    fun View.getActivity(): Activity? {
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val binding: FragmentSpectrogramBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_spectrogram,
            container,
            false
        )

        // Check for recording permission
        if (ContextCompat.checkSelfPermission(this.context!!, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
            ActivityCompat.requestPermissions(this.activity!!, permissions,0)
        }

        viewModel = ViewModelProviders.of(this).get(SpectrogramViewModel::class.java)

        viewModel.resultString.observe(this, Observer { newResultString ->
            binding.spectrogramView.text = newResultString
        })

        // Disable the record button while recording is in progress
        viewModel.isRecording.observe(this, Observer { recording ->
            if (recording) {
                binding.recordButton.isEnabled = false
                binding.recordButton.isClickable = false
            }
            else {
                binding.recordButton.isEnabled = true
                binding.recordButton.isClickable = true
            }
        })

        // Update the chart when the signal is ready
        viewModel.signal.observe(this, Observer { newSignal ->
            val entries1 = ArrayList<Entry>()
            for (i in 0 until newSignal.size) {
                entries1.add(Entry(i.toFloat(), newSignal[i]))
            }
            val dataSet1 = LineDataSet(entries1, "raw data")
            val lineData1 = LineData(dataSet1)
            binding.chart1.data = lineData1
            binding.chart1.invalidate()
        })

        // Update the tone prediction text views when the model outputs change
        viewModel.modelOutputs.observe(this, Observer {newOutputs ->
            binding.tone1Text.text = getString(R.string.tone_1_result).format(newOutputs[0][0]*100)
            binding.tone2Text.text = "Tone 2 probability: %.1f".format(newOutputs[0][1]*100)
            binding.tone3Text.text = "Tone 3 probability: %.1f".format(newOutputs[0][2]*100)
            binding.tone4Text.text = "Tone 4 probability: %.1f".format(newOutputs[0][3]*100)
            binding.tone5Text.text = "Tone 5 probability: %.1f".format(newOutputs[0][4]*100)
        })

        // Set on click listeners for buttons
        binding.button.setOnClickListener {
            binding.spectrogramView.text = ""
            viewModel.getSpectrogram(this.context!!)
        }
        //binding.recordButton.setOnClickListener {
        //    viewModel.startRecording()
        //}

        binding.recordButton.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {

                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> CoroutineScope(Dispatchers.IO).launch {
                        viewModel.startRecording()
                    }
                    MotionEvent.ACTION_UP -> viewModel.stopRecording()
                }

                return v?.onTouchEvent(event) ?: true
            }
        })

        binding.predictButton.setOnClickListener {
            viewModel.classifySequence(context!!)
        }

        return binding.root
    }

}