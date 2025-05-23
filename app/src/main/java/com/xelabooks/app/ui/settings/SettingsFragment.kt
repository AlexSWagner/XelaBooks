package com.xelabooks.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.xelabooks.app.databinding.FragmentSettingsBinding
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupSpinner()
        setupListeners()
    }
    
    private fun setupSpinner() {
        // Set up sleep timer options
        val sleepTimerOptions = arrayOf(
            "Off", 
            "15 minutes", 
            "30 minutes", 
            "45 minutes", 
            "1 hour", 
            "End of chapter"
        )
        
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            sleepTimerOptions
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSleepTimer.adapter = adapter
    }
    
    private fun setupListeners() {
        // Clear cache button
        binding.btnClearCache.setOnClickListener {
            clearCache()
        }
        
        // Switch listeners for saving preferences
        binding.switchAutoResume.setOnCheckedChangeListener { _, isChecked ->
            savePreference("auto_resume", isChecked)
        }
        
        binding.switchSkipSilence.setOnCheckedChangeListener { _, isChecked ->
            savePreference("skip_silence", isChecked)
        }
        
        // Privacy policy
        binding.btnPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }
        
        // Rate app
        binding.btnRateApp.setOnClickListener {
            rateApp()
        }
    }
    
    private fun clearCache() {
        try {
            // Clear the app's cache directory
            val cacheDir = requireContext().cacheDir
            deleteRecursive(cacheDir)
            
            Toast.makeText(requireContext(), "Cache cleared successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to clear cache", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    
    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        fileOrDirectory.delete()
    }
    
    private fun savePreference(key: String, value: Boolean) {
        // Save to SharedPreferences
        val prefs = requireContext().getSharedPreferences("audiobookmanager_prefs", 0)
        prefs.edit().putBoolean(key, value).apply()
    }
    
    private fun openPrivacyPolicy() {
        // This would normally point to your privacy policy URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/privacy-policy"))
        startActivity(intent)
    }
    
    private fun rateApp() {
        // Open Play Store to rate the app
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=${requireContext().packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // If Play Store app is not installed, open in browser
            val webIntent = Intent(Intent.ACTION_VIEW, 
                Uri.parse("https://play.google.com/store/apps/details?id=${requireContext().packageName}"))
            startActivity(webIntent)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 