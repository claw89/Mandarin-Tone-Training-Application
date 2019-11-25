package com.example.lambdaspectrogram

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.AsyncTask
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunctionException
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaInvokerFactory
import com.amazonaws.regions.Regions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import khttp.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.io.ByteArrayOutputStream
import org.tensorflow.lite.Interpreter

class GameViewModel: ViewModel() {

    //lateinit var myInterface: MyInterface

    private val SAMPLING_RATE = 44100
    private val CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT
    private val BUFFER = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_IN_CONFIG, AUDIO_FORMAT)
    val audioRecorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLING_RATE,
        CHANNEL_IN_CONFIG,
        AUDIO_FORMAT,
        BUFFER)
    private var record = false
    private val audioData = ByteArray(BUFFER)
    private val os = ByteArrayOutputStream()

//    private val _modelInputs = MutableLiveData<Array<Array<Array<FloatArray>>>>()
//    val modelInputs: LiveData<Array<Array<Array<FloatArray>>>>
//        get() = _modelInputs
//    var outputs : Array<FloatArray> = arrayOf( floatArrayOf( 0.0f , 0.0f , 0.0f , 0.0f , 0.0f) )


    // The current word
    private val _word = MutableLiveData<Triple<String, List<Int>, Int>>()
    val word: LiveData<Triple<String, List<Int>, Int>>
        get() = _word

    // The current score
    private val _score = MutableLiveData<Int>()
    val score: LiveData<Int>
        get() = _score

    // The predicted tone
    private val _predictedTones = MutableLiveData<List<Int>>()
    val predictedTones: LiveData<List<Int>>
        get() = _predictedTones

    // Correct tones predicted
    private val _correctTones = MutableLiveData<Boolean>()
    val correctTones: LiveData<Boolean>
        get() = _correctTones

    // The list of words - the front of the list is the next word to guess
    private lateinit var wordList: MutableList<Triple<String, List<Int>, Int>>

    // Event which triggers the end of the game
    private val _eventGameFinish = MutableLiveData<Boolean>()
    val eventGameFinish: LiveData<Boolean>
        get() = _eventGameFinish

    init {
        resetList()
        nextWord()
        _score.value = 0
        _eventGameFinish.value = false
    }

    fun playAudio(context: Context) {
        val mediaPlayer = MediaPlayer.create(context, _word.value!!.third)
        mediaPlayer.start()
    }

    fun nextWord() {
        //Select and remove a word from the list
        if (wordList.isEmpty()) {
            _eventGameFinish.value = true
            //resetList()
        }
        else {
            _word.value = wordList.removeAt(0)
        }
    }

    fun scorePlusOne() {
        if (_correctTones.value!!) {
            _score.value = _score.value!! + 1
        }
    }

    private fun resetList() {
        wordList = mutableListOf(
            Triple("三", listOf(1), R.raw.san1),
            Triple("前", listOf(2), R.raw.qian2),
            Triple("有", listOf(3), R.raw.you3),
            Triple("四", listOf(4), R.raw.si4),
            Triple("朋友", listOf(2, 3), R.raw.peng2you3),
            Triple("多少", listOf(1, 3), R.raw.duo1shao3),
            Triple("工作", listOf(1, 4), R.raw.gong1zuo4),
            Triple("今天", listOf(1, 1), R.raw.jin1tian1),
            Triple("老師", listOf(3, 1), R.raw.lao3shi1),
            Triple("明天", listOf(2, 1), R.raw.ming2tian1)
        )
        wordList.shuffle()
    }

    fun startRecording() {
        record = true
        _predictedTones.postValue(listOf())
        Log.i("GameViewModel", "function started")
        audioRecorder.startRecording()
        Log.i("GameViewModel", "started recorder")
        while (record) {
            audioRecorder.read(audioData, 0, audioData.size)
            os.write(audioData, 0, audioData.size)
            Log.i("GameViewModel", "record = $record; recording")
        }
    }

    fun stopRecording() {
        record = false
        Log.i("GameViewModel", "record = $record; stopping recording")
        audioRecorder.stop()
        Log.i("GameViewModel", "stopped recording")
        val data: MutableList<Float> = mutableListOf()
        for (item in os.toByteArray()) {
            data.add(item.toFloat())
        }
        val dataMin = data.min()
        val dataMax = data.max()
        for (i in 0 until data.size) {
            if (data[i]> 0) {
                data[i] = data[i] - dataMax!!
            }
            else {
                data[i] = data[i] - dataMin!!
            }
        }
        val numSyllables = _word.value!!.first.length
        val url = "https://pvxqcafn71.execute-api.us-east-1.amazonaws.com/dev/predict"
        val payload = mapOf("num_syl" to numSyllables.toString())
        val headers = mapOf("x-api-key" to "WjxBRA2JGt1NnPZjyeQHU8oIg70uGkhX7lyPgO0J")
        val jsonData = Gson().toJson(data)

        CoroutineScope(Dispatchers.IO).launch {
            var correct = true
            val tones = mutableListOf<Int>()
            val response = post(url=url, params=payload, headers=headers, data=jsonData)
            Log.i("GameViewModel", response.statusCode.toString())
            if (response.statusCode == 200) {
                val predictionsListType = object : TypeToken<List<List<Float>>>() {}.type
                val predictions = Gson().fromJson<List<List<Float>>>(
                    response.jsonObject.getString("predictions"),
                    predictionsListType
                )
                for (prediction in predictions) {
                    for (item in prediction) {
                        Log.i("GameViewModel", item.toString())
                    }
                    Log.i("GameViewModel", (prediction.withIndex().maxBy { it.value }!!.index + 1).toString())
                    tones.add(prediction.withIndex().maxBy { it.value }!!.index + 1)

                }
                val actualTones = _word.value!!.second
                for ((actualTone, predictedTone) in actualTones zip tones) {
                    if (actualTone != predictedTone)
                        correct = false
                }
                _predictedTones.postValue(tones)
            }
            else {
                correct = false
                for (i in 0..numSyllables) {
                    tones.add(0)
                }
                _predictedTones.postValue(tones)
            }
            _correctTones.postValue(correct)
        }
        os.reset()
    }

//        val cognitoProvider = CognitoCachingCredentialsProvider(
//            applicationContext, "us-east-1:63de3df8-f7a4-4c62-a0d1-fe8726cdc525", Regions.US_EAST_1
//        )
//
//        // Create LambdaInvokerFactory, to be used to instantiate the Lambda proxy.
//        val factory = LambdaInvokerFactory(
//            applicationContext,
//            Regions.US_EAST_1, cognitoProvider
//        )
//        // Create the Lambda proxy object with a default Json data binder.
//        // You can provide your own data binder by implementing
//        // LambdaDataBinder.
//        myInterface = factory.build(MyInterface::class.java)
//        val request = RequestClass(data, _word.value!!.first.length)
//        // The Lambda function invocation results in a network call.
//        // Make sure it is not called from the main thread.
//        MyAsyncTask(this).execute(request)


//    fun classifySequence (interpreter: Interpreter, inputs: Array<Array<Array<FloatArray>>>): Int? {
//        interpreter.run(inputs , outputs)
//        val maxIndex = indexOfMax(outputs[0])
//        var predictedTone = 0
//        if (maxIndex != null) {
//            predictedTone = maxIndex + 1
//        }
//        return predictedTone
//    }
//
//    private fun indexOfMax(a: FloatArray): Int? {
//
//        if (a.size == 0)
//            return null
//
//        var max: Float = Float.MIN_VALUE
//        var maxPosition = 0
//
//        for (i in a.indices) {
//            if (a[i] >= max) {
//                max = a[i]
//                maxPosition = i
//            }
//        }
//        return maxPosition
//    }
//
//    private class MyAsyncTask internal constructor(context: GameViewModel): AsyncTask<RequestClass, Void, ResponseClass>() {
//
//        private val activityReference: WeakReference<GameViewModel> = WeakReference(context)
//
//
//        override fun doInBackground(vararg params: RequestClass): ResponseClass? {
//            // invoke "echo" method. In case it fails, it will throw a
//            // LambdaFunctionException.
//
//            var response: ResponseClass?
//            val activity = activityReference.get()
//            if (activity != null) {
//                try {
//                    response = activity.myInterface.AndroidToneBackendLambdaFunction(params[0])
//                } catch (lfe: LambdaFunctionException) {
//                    Log.e("Tag", "Failed to invoke echo", lfe)
//                    response = null
//                }
//            }
//            else {
//                response = null
//            }
//            return response
//        }
//
//        override fun onPostExecute(result: ResponseClass?) {
//            if (result == null) {
//                return
//            }
//
//            // Set the result string
//            val activity = activityReference.get()
//            if (activity != null) {
//                activity._modelInputs.value = result.body[0]
//
//            }
//        }
//    }
//
}