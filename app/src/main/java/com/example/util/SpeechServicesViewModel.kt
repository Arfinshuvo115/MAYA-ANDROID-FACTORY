package com.example.util

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Retains speech engines across Activity configuration changes and releases them
 * only when the owning Activity ViewModelStore is cleared.
 */
class SpeechServicesViewModel(context: Context) : ViewModel() {
    val speechHelper = SpeechHelper(context.applicationContext)
    val speechRecognizerHelper = SpeechRecognizerHelper(context.applicationContext)

    override fun onCleared() {
        speechHelper.shutdown()
        speechRecognizerHelper.destroy()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != SpeechServicesViewModel::class.java) {
                throw IllegalArgumentException("Unsupported ViewModel: ${modelClass.name}")
            }
            return SpeechServicesViewModel(context.applicationContext) as T
        }
    }
}
