package com.example.lambdaspectrogram

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import khttp.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.collections.Map
import kotlin.math.min
import kotlin.math.roundToInt

class GameViewModel: ViewModel() {

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

    // The current word
    private val _word = MutableLiveData<Triple<String, List<Int>, Int>>()
    val word: LiveData<Triple<String, List<Int>, Int>>
        get() = _word

    // The current score
    private val _score = MutableLiveData<Int>()
    val score: LiveData<Int>
        get() = _score

    // The predicted tones
    private val _predictedTones = MutableLiveData<List<Int>>()
    val predictedTones: LiveData<List<Int>>
        get() = _predictedTones

    // Correct tones predicted
    private val _correctTones = MutableLiveData<Boolean>()
    val correctTones: LiveData<Boolean>
        get() = _correctTones

    // Event which triggers the end of the game
    private val _eventGameFinish = MutableLiveData<Boolean>()
    val eventGameFinish: LiveData<Boolean>
        get() = _eventGameFinish

    // Event which triggers the end of the game
    private val _bitmapDrawable = MutableLiveData<BitmapDrawable>()
    val bitmapDrawable: LiveData<BitmapDrawable>
        get() = _bitmapDrawable

    // The list of words - the front of the list is the next word to guess
    private lateinit var wordList: MutableList<Triple<String, List<Int>, Int>>

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

    fun stopRecording(resources: Resources) {
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
            val cnnTones = mutableListOf<Int>()
            val rnnTones = mutableListOf<Int>()
            val avgTones = mutableListOf<Int>()
            val tones = mutableListOf<Int>()
            val response = post(url=url, params=payload, headers=headers, data=jsonData)
            Log.i("GameViewModel", response.statusCode.toString())
            if (response.statusCode == 200) {
                val predictionsListType = object : TypeToken<List<List<Float>>>() {}.type
                val cnnPredictions = Gson().fromJson<List<List<Float>>>(
                    response.jsonObject.getString("cnn_predictions"),
                    predictionsListType
                )
                val rnnPredictions = Gson().fromJson<List<List<Float>>>(
                    response.jsonObject.getString("rnn_predictions"),
                    predictionsListType
                )
                for (predictionPair in cnnPredictions.zip(rnnPredictions)) {
                    Log.i("GameViewModel", "CNN Predictions")
                    for (item in predictionPair.first) {
                        Log.i("GameViewModel", item.toString())
                    }
                    cnnTones.add(predictionPair.first.withIndex().maxBy { it.value }!!.index + 1)
                    Log.i("GameViewModel", "RNN Predictions")
                    for (item in predictionPair.second) {
                        Log.i("GameViewModel", item.toString())
                    }
                    rnnTones.add(predictionPair.second.withIndex().maxBy { it.value }!!.index + 1)
                    val prediction = predictionPair.first.zip(predictionPair.second).map { (a, b) -> a + b }
                    Log.i("GameViewModel", "Averaged Predictions")
                    for (item in prediction) {
                        Log.i("GameViewModel", item.toString())
                    }
                    Log.i("GameViewModel", (prediction.withIndex().maxBy { it.value }!!.index + 1).toString())
                    avgTones.add(prediction.withIndex().maxBy { it.value }!!.index + 1)

                }
                val actualTones = _word.value!!.second
                val predictedTones = cnnTones.zip(rnnTones).zip(avgTones) { (a, b), c -> Triple(a, b, c)}
                for ((actualTone, predictedTone) in actualTones.zip(predictedTones)) {
                    if (actualTone == 3) {
                        tones.add(predictedTone.second)
                        if (predictedTone.second != 3) {
                            correct = false
                        }
                    }
                    else {
                        tones.add(predictedTone.third)
                        if (actualTone != predictedTone.third) {
                            correct = false
                        }
                    }
                }
                _predictedTones.postValue(tones)

                val mapListType = object : TypeToken<List<Map<String, List<Float>>>>() {}.type
                val maps = Gson().fromJson<List<Map<String, List<Float>>>>(
                    response.jsonObject.getString("maps"),
                    mapListType
                )
//                Log.i("GameViewModel", maps[0].get("x").toString())
//                Log.i("GameViewModel", maps[0].get("y").toString())
//                Log.i("GameViewModel", maps[0].get("z").toString())
//                Log.i("GameViewModel", maps[0].get("z")!![10].toString())
                val bitmap = Bitmap.createBitmap(_word.value!!.first.length*150, 150, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val shapeDrawable = ShapeDrawable(RectShape())
                for ((i, map) in maps.withIndex()) {
                    for (j in 0 until map.get("x")!!.size) {
                        shapeDrawable.setBounds(
                            map.get("x")!![j].toInt() + (150 * i),
                            map.get("y")!![j].toInt(),
                            map.get("x")!![j].toInt() + (150 * i) + 1,
                            map.get("y")!![j].toInt() + 1
                        )
                        //Log.i("GameViewModel", (map.get("z")!![i].toString()))
                        //Log.i("GameViewModel", (map.get("z")!![i] * 255).toString())
                        //Log.i("GameViewModel", (map.get("z")!![i] * 255).roundToInt().toString())
                        val opacity = Integer.toHexString(min((map.get("z")!![j] * 255).roundToInt(), 255))
                        //Log.i("GameViewModel", opacity)
                        shapeDrawable.getPaint().setColor(Color.parseColor("#" + opacity + "000000"))
                        shapeDrawable.draw(canvas)
                    }
                }
                _bitmapDrawable.postValue(BitmapDrawable(resources, bitmap))
            }
            else {
                for (i in 0..numSyllables) {
                    tones.add(0)
                }
                _predictedTones.postValue(tones)
            }
            _correctTones.postValue(correct)
        }
        os.reset()
    }
}
