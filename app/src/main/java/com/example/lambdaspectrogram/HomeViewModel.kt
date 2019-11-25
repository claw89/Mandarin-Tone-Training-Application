package com.example.lambdaspectrogram

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import khttp.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException

class HomeViewModel: ViewModel() {

    // Live data to report the modules are loaded
    private val _loaded = MutableLiveData<Boolean>()
    val loaded: LiveData<Boolean>
        get() = _loaded

    init {
        _loaded.value = false
    }

    fun loadModules(exampleData: String) {
        val url = "https://************************.amazonaws.com/dev/predict"
        val payload = mapOf("num_syl" to "2")
        val headers = mapOf("x-api-key" to "***********************************")
        val jsonData = Gson().toJson(exampleData)
        CoroutineScope(Dispatchers.IO).launch {
            var statusCode = 504
            while (statusCode == 504) {
                try {
                    val response = post(url = url, params = payload, headers = headers, data = jsonData)
                    statusCode = response.statusCode
                }
                catch (e: SocketTimeoutException) {
                    Log.i("HomeViewModel", e.toString())
                }
            }
            _loaded.postValue(true)
        }
    }
}
