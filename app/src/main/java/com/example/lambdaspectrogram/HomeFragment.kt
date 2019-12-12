package com.example.lambdaspectrogram

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.example.lambdaspectrogram.databinding.FragmentHomeBinding
import java.io.InputStream


class HomeFragment: Fragment() {
    private lateinit var viewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val binding: FragmentHomeBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_home,
            container,
            false
        )

        // Check for recording permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
            ActivityCompat.requestPermissions(requireActivity(), permissions,0)
        }

        viewModel = ViewModelProviders.of(this).get(HomeViewModel::class.java)

        viewModel.loadModules(readJSONFromAsset("data.json")!!)

        viewModel.loaded.observe(this, Observer { loaded ->
            if (loaded) {
                binding.playGameButton.isEnabled = true
                binding.playGameButton.isClickable = true
                binding.loadingInfo.visibility = View.INVISIBLE
                binding.progressBar.visibility = View.INVISIBLE
            }
        })

        binding.playGameButton.setOnClickListener {view ->
            Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_gameFragment)
        }

        return binding.root
    }

    private fun readJSONFromAsset(name: String): String? {
        val json: String?
        try {
            val  inputStream: InputStream = context!!.assets.open(name)
            json = inputStream.bufferedReader().use{it.readText()}
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }
        return json
    }
}