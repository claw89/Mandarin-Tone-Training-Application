package com.example.lambdaspectrogram

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.security.NetworkSecurityPolicy
import android.util.Log
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
import androidx.navigation.Navigation
import com.example.lambdaspectrogram.databinding.FragmentGameBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import khttp.get
import khttp.post
import khttp.responses.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class GameFragment: Fragment() {
    private lateinit var viewModel: GameViewModel

//    private fun loadModelFile(modelAssetsPath: String, context: Context): MappedByteBuffer {
//        val assetFileDescriptor = context.assets.openFd(modelAssetsPath)
//        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
//        val fileChannel = fileInputStream.channel
//        val startOffset = assetFileDescriptor.startOffset
//        val declaredLength = assetFileDescriptor.declaredLength
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
//    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val binding: FragmentGameBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_game,
            container,
            false
        )

        //val interpreter = Interpreter(loadModelFile("tone_model.tflite", requireContext()))

        // Check for recording permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
            ActivityCompat.requestPermissions(requireActivity(), permissions,0)
        }

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
                binding.resultTextView.text = "Correct!"
            }
            else {
                binding.resultTextView.text = "Try again..."
            }
        })

//        viewModel.predictedTones.observe(this, Observer {newPredictedTones ->
//            binding.progressBar1.visibility = View.INVISIBLE
//            if (newPredictedTones.isEmpty()) {
//                binding.resultTextView.text = ""
//            }
//            else {
//                var correct = true
//                val actualTones = viewModel.word.value!!.second
//                for ((actualTone, predictedTone) in actualTones zip newPredictedTones) {
//                    if (actualTone != predictedTone)
//                        correct = false
//                }
//                if (correct) {
//                    binding.resultTextView.text = "Correct!"
//                } else {
//                    binding.resultTextView.text = "Try again..."
//                }
//            }
//        })

//        viewModel.modelInputs.observe(this, Observer { newModelInputs ->
//            binding.progressBar1.visibility = View.INVISIBLE
//            val predictedTone = viewModel.classifySequence(interpreter, newModelInputs)
//            if (predictedTone == viewModel.word.value!!.second) {
//                binding.resultTextView.text = "Correct!"
//            }
//            else {
//                binding.resultTextView.text = "Try again..."
//            }
//        })

        binding.nextButton.setOnClickListener {
            binding.resultTextView.text = ""
            viewModel.scorePlusOne()
            viewModel.nextWord()
            binding.nextButton.isEnabled = false
            binding.nextButton.isClickable = false
//            val inputListType = object : TypeToken<List<Float>>() { }.type
//            val inputData = Gson().fromJson<List<Float>>(readJSONFromAsset()!!, inputListType)
//            Log.i("GameFragment", inputData.toString())
//            Log.i("GameFragment", inputData.size.toString())
//            Log.i("GameFragment", Gson().toJson(inputData))
//
//            CoroutineScope(Dispatchers.IO).launch {
//                val url = "https://pvxqcafn71.execute-api.us-east-1.amazonaws.com/dev/predict"
//                val payload = mapOf("num_syl" to "2")
//                val headers = mapOf("x-api-key" to "WjxBRA2JGt1NnPZjyeQHU8oIg70uGkhX7lyPgO0J")
//                val data = readJSONFromAsset()!!
//
//                val response = post(url=url, params=payload, headers=headers, data=data)
//                val predictionsListType = object : TypeToken<List<List<Float>>>() { }.type
//                val predictions = Gson().fromJson<List<List<Float>>>(response.jsonObject.getString("predictions"), predictionsListType)
//                for (prediction in predictions) {
//                    for (item in prediction) {
//                        Log.i("GameFragment", item.toString())
//                    }
//                    Log.i("GameFragment", (prediction.withIndex().maxBy { it.value }!!.index + 1).toString())
//                }
//                Log.i("GameFragment", response.statusCode.toString())
//                Log.i("GameFragment", response.jsonObject.getString("predictions"))
//            }

//            val obj = JSONObject(readJSONFromAsset()!!)
//            val data = obj.getJSONArray("data")
//            val dataList = data.toString().slice(1 until data.length()).split(",")
//
//            Log.i("GameFragment", data.toString())
//            Log.i("GameFragment", dataList.first())
//            Log.i("GameFragment", dataList.last())
//            Log.i("GameFragment", dataList.size.toString())
//
//            CoroutineScope(Dispatchers.IO).launch {
//                val ipAddress = get(url = "http://httpbin.org/ip").jsonObject.getString("origin")
//                Log.i("GameFragment", ipAddress)
//            }
        }

        binding.button4.setOnClickListener {
            viewModel.playAudio(requireContext())
        }

        binding.recordButton.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {

                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> CoroutineScope(Dispatchers.IO).launch {
                        viewModel.startRecording()
                    }
                    MotionEvent.ACTION_UP -> {
                        binding.progressBar1.visibility = View.VISIBLE
                        viewModel.stopRecording()
                    }
                }

                return v?.onTouchEvent(event) ?: true
            }
        })

        return binding.root
    }




}