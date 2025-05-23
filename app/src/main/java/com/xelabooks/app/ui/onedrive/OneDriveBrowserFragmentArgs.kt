package com.xelabooks.app.ui.onedrive

import android.os.Bundle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException

/**
 * Arguments for the OneDriveBrowserFragment
 */
data class OneDriveBrowserFragmentArgs(
    val isForCover: Boolean = false
) : NavArgs {
    
    fun toBundle(): Bundle {
        val result = Bundle()
        result.putBoolean("isForCover", isForCover)
        return result
    }
    
    companion object {
        @JvmStatic
        fun fromBundle(bundle: Bundle): OneDriveBrowserFragmentArgs {
            bundle.setClassLoader(OneDriveBrowserFragmentArgs::class.java.classLoader)
            
            if (!bundle.containsKey("isForCover")) {
                return OneDriveBrowserFragmentArgs()
            }
            
            val isForCover = bundle.getBoolean("isForCover")
            return OneDriveBrowserFragmentArgs(isForCover)
        }
        
        @JvmStatic
        fun fromSavedStateHandle(savedStateHandle: androidx.lifecycle.SavedStateHandle): OneDriveBrowserFragmentArgs {
            val isForCover = savedStateHandle.get<Boolean>("isForCover") ?: false
            return OneDriveBrowserFragmentArgs(isForCover)
        }
    }
} 