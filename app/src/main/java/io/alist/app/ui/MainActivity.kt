package io.alist.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.alist.app.R
import io.alist.app.alist.AListForegroundService
import io.alist.app.alist.AListManager
import io.alist.app.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_CODE = 1001
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var alistManager: AListManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alistManager = AListManager.getInstance(this)

        requestNotificationPermission()
        setupUI()
        startAListService()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    private fun setupUI() {
        binding.btnStartStop.setOnClickListener {
            if (alistManager.isAListRunning()) {
                AListForegroundService.stop(this)
                binding.tvStatus.text = getString(R.string.alist_stopped)
                binding.btnStartStop.text = "Start"
                binding.btnOpenWebview.isEnabled = false
            } else {
                startAListService()
            }
        }

        binding.btnOpenWebview.setOnClickListener {
            startActivity(Intent(this, WebViewActivity::class.java))
        }

        binding.tvStatus.text = getString(R.string.alist_not_ready)
        binding.btnOpenWebview.isEnabled = false
        binding.btnStartStop.text = "Stop"

        lifecycleScope.launch {
            delay(2000)
            if (alistManager.isAListRunning()) {
                binding.tvStatus.text = getString(R.string.alist_started)
                binding.btnOpenWebview.isEnabled = true
                binding.btnStartStop.text = "Stop"
            } else {
                binding.tvStatus.text = getString(R.string.alist_not_ready)
            }
        }
    }

    private fun startAListService() {
        binding.tvStatus.text = getString(R.string.alist_not_ready)
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                alistManager.extractBinary()
                AListForegroundService.start(this@MainActivity)

                delay(3000)
                binding.progressBar.visibility = View.GONE

                if (alistManager.isAListRunning()) {
                    binding.tvStatus.text = getString(R.string.alist_started)
                    binding.btnOpenWebview.isEnabled = true
                    binding.btnStartStop.text = "Stop"
                } else {
                    binding.tvStatus.text = getString(R.string.alist_stopped)
                    binding.btnStartStop.text = "Start"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting AList", e)
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Error: ${e.message}"
                Toast.makeText(this@MainActivity, "Failed to start AList", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
