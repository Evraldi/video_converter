package com.example.converted_video

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig

class MainActivity : AppCompatActivity() {
    private lateinit var inputUri: Uri
    private lateinit var outputFormat: String
    private var outputFileName: String? = null

    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                outputFileName?.let { performVideoConversion(it) }
            } else {
                // Handle permission denied
            }
        }

    private val selectVideoLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    inputUri = uri
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val selectButton: Button = findViewById(R.id.selectButton)
        val convertButton: Button = findViewById(R.id.convertButton)
        val formatSpinner: Spinner = findViewById(R.id.formatSpinner)
        val outputEditText: EditText = findViewById(R.id.outputEditText)

        selectButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
            }
            selectVideoLauncher.launch(intent)
        }

        convertButton.setOnClickListener {
            outputFormat = formatSpinner.selectedItem.toString()
            outputFileName = outputEditText.text.toString()
            if (outputFileName.isNullOrEmpty() || !::inputUri.isInitialized) {
                // Handle missing file name or input URI
                return@setOnClickListener
            }
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> performVideoConversion(outputFileName!!)
        }
    }

    private fun performVideoConversion(outputFileName: String) {
        val outputPath = getOutputFilePath(outputFileName)
        convertVideo(outputPath)
    }

    private fun getOutputFilePath(fileName: String): String {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$fileName.$outputFormat")
            put(MediaStore.Video.Media.MIME_TYPE, "video/$outputFormat")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ConvertedVideos")
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        return uri?.let { getPathFromUri(it) } ?: ""
    }

    private fun getPathFromUri(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val pathIndex = it.getColumnIndex(MediaStore.Video.Media.DATA)
                if (pathIndex != -1) {
                    return it.getString(pathIndex)
                }
            }
        }
        return ""
    }

    private fun convertVideo(outputPath: String) {
        val inputPath = FFmpegKitConfig.getSafParameterForRead(this, inputUri)
        // Updated FFmpeg command to include audio
        val command = "-i $inputPath -c:v libx264 -c:a aac -strict experimental $outputPath"
        FFmpegKit.execute(command)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                outputFileName?.let { performVideoConversion(it) }
            } else {
                // Handle permission denied
            }
        }
    }

    companion object {
        private const val REQUEST_READ_STORAGE_PERMISSION = 1001
    }
}
