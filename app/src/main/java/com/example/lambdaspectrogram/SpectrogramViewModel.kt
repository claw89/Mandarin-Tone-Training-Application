//package com.example.lambdaspectrogram
//
//import android.Manifest
//import android.app.Activity
//import android.content.Context
//import android.content.pm.PackageManager
//import android.media.AudioFormat
//import android.media.AudioRecord
//import android.media.MediaRecorder
//import android.os.AsyncTask
//import android.util.Log
//import android.view.MotionEvent
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.ViewModel
//import com.amazonaws.auth.CognitoCachingCredentialsProvider
//import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunctionException
//import com.amazonaws.mobileconnectors.lambdainvoker.LambdaInvokerFactory
//import com.amazonaws.regions.Regions
//import java.lang.ref.WeakReference
//import java.io.ByteArrayOutputStream
//import java.io.FileInputStream
//import java.nio.MappedByteBuffer
//import java.nio.channels.FileChannel
//import org.tensorflow.lite.Interpreter
//
//class SpectrogramViewModel: ViewModel() {
//
//    lateinit var myInterface: MyInterface
//    private val _signal = MutableLiveData<MutableList<Float>>()
//    val signal: LiveData<MutableList<Float>>
//        get() = _signal
//    private val _resultString = MutableLiveData<String>()
//    val resultString: LiveData<String>
//        get() = _resultString
//
//    private val _modelOutputs = MutableLiveData<Array<FloatArray>>()
//    val modelOutputs: LiveData<Array<FloatArray>>
//        get() = _modelOutputs
//
//    private val SAMPLING_RATE = 44100
//    private val CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO
//    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT
//    private val BUFFER = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_IN_CONFIG, AUDIO_FORMAT)
//    val audioRecorder = AudioRecord(
//        MediaRecorder.AudioSource.MIC,
//        SAMPLING_RATE,
//        CHANNEL_IN_CONFIG,
//        AUDIO_FORMAT,
//        BUFFER)
//    private val _isRecording = MutableLiveData<Boolean>()
//    val isRecording: LiveData<Boolean>
//        get() = _isRecording
//    init {
//        _isRecording.value = false
//        _modelOutputs.value = arrayOf( floatArrayOf( 0.0f , 0.0f , 0.0f , 0.0f , 0.0f) )
//    }
//
//    private var modelInputs: Array<Array<Array<FloatArray>>> = arrayOf(arrayOf(arrayOf(FloatArray(1))))
//    var outputs : Array<FloatArray> = arrayOf( floatArrayOf( 0.0f , 0.0f , 0.0f , 0.0f , 0.0f) )
//
//    private var record = false
//    private val audioData = ByteArray(BUFFER)
//    private val os = ByteArrayOutputStream()
//
//
//
//    suspend fun startRecording() {
//        record = true
//        Log.i("SpectrogramViewModel", "function started")
//        audioRecorder.startRecording()
//        Log.i("SpectrogramViewModel", "started recorder")
//        while (record) {
//            audioRecorder.read(audioData, 0, audioData.size)
//            os.write(audioData, 0, audioData.size)
//            Log.i("SpectrogramViewModel", "record = $record; recording")
//        }
//    }
//
//    fun stopRecording() {
//        record = false
//        Log.i("SpectrogramViewModel", "record = $record; stopping recording")
//        audioRecorder.stop()
//        Log.i("SpectrogramViewModel", "stopped recording")
//        val data: MutableList<Float> = mutableListOf()
//        for (item in os.toByteArray()) {
//            data.add(item.toFloat())
//        }
//        val dataMin = data.min()
//        val dataMax = data.max()
//        for (i in 0 until data.size) {
//            if (data[i]> 0) {
//                data[i] = data[i] - dataMax!!
//            }
//            else {
//                data[i] = data[i] - dataMin!!
//            }
//        }
//        _signal.value = data
//        Log.i("SpectrogramViewModel", "signal size: ${os.size()}")
//        Log.i("SpectrogramViewModel", "signal size: ${data.size}")
//        Log.i("SpectrogramViewModel", "signal size: ${_signal.value?.size}")
//        os.reset()
//    }
//
//    fun getSpectrogram(applicationContext: Context) {
//        if (_signal.value != null) {
//            // Create an instance of CognitoCachingCredentialsProvider
//            val cognitoProvider = CognitoCachingCredentialsProvider(
//                applicationContext, "*****Add the AWS Cognito Identity pool ID*****", Regions.US_EAST_1
//            )
//
//            // Create LambdaInvokerFactory, to be used to instantiate the Lambda proxy.
//            val factory = LambdaInvokerFactory(
//                applicationContext,
//                Regions.US_EAST_1, cognitoProvider
//            )
//            // Create the Lambda proxy object with a default Json data binder.
//            // You can provide your own data binder by implementing
//            // LambdaDataBinder.
//            myInterface = factory.build(MyInterface::class.java)
//            val request = RequestClass(_signal.value!!, 1)
//            // The Lambda function invocation results in a network call.
//            // Make sure it is not called from the main thread.
//            MyAsyncTask(this).execute(request)
//        }
//    }
//
//    private fun loadModelFile(modelAssetsPath: String, context: Context): MappedByteBuffer {
//        val assetFileDescriptor = context.assets.openFd(modelAssetsPath)
//        val fileInputStream = FileInputStream(assetFileDescriptor.getFileDescriptor())
//        val fileChannel = fileInputStream.getChannel()
//        val startOffset = assetFileDescriptor.getStartOffset()
//        val declaredLength = assetFileDescriptor.getDeclaredLength()
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
//    }
//
//    fun classifySequence (context: Context) {
//        val interpreter = Interpreter(loadModelFile("tone_model.tflite", context))
//        Log.i("MainActivity", interpreter.getInputTensor(0).shape().contentToString())
//        interpreter.run(modelInputs , outputs)
//        _modelOutputs.value =  outputs
//    }
//
//    private class MyAsyncTask internal constructor(context: SpectrogramViewModel): AsyncTask<RequestClass, Void, ResponseClass>() {
//
//        private val activityReference: WeakReference<SpectrogramViewModel> = WeakReference(context)
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
//                activity._resultString.value = "Obtained spectrogram with dimensions ${result.shape} "
//                activity.modelInputs = result.body[0]
//            }
//        }
//    }
//
//}
