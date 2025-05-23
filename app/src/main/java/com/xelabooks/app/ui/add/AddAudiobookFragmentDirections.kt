package com.xelabooks.app.ui.add

import android.os.Bundle
import androidx.navigation.NavDirections
import com.xelabooks.app.R

/**
 * Navigation directions for AddAudiobookFragment
 */
class AddAudiobookFragmentDirections private constructor() {
    
    /**
     * Navigate to OneDrive browser
     */
    data class ActionNavigationAddAudiobookToOneDriveBrowser(
        val isForCover: Boolean = false
    ) : NavDirections {
        override val actionId: Int = R.id.action_navigation_add_audiobook_to_oneDriveBrowser
        
        override val arguments: Bundle
            get() {
                val result = Bundle()
                result.putBoolean("isForCover", isForCover)
                return result
            }
    }
    
    companion object {
        /**
         * Navigate to OneDrive browser
         */
        fun actionNavigationAddAudiobookToOneDriveBrowser(isForCover: Boolean = false): NavDirections {
            return ActionNavigationAddAudiobookToOneDriveBrowser(isForCover)
        }
    }
} 