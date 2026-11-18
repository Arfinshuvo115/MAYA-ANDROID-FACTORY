package com.example.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.local.ConversationStore
import com.example.data.local.PreferencesRepository
import com.example.domain.model.AiWorkflowTool
import com.example.domain.model.StudioRunState
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ToolsViewModel(
    private val aiRepository: AiRepository,
    private val conversationStore: ConversationStore,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val preferencesState = preferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences()
    )

    private val _selectedTool = MutableStateFlow(AiWorkflowTool.SUMMARIZER)
    val selectedTool: StateFlow<AiWorkflowTool> = _selectedTool.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _attachedImageUri = MutableStateFlow<String?>(null)
    val attachedImageUri: StateFlow<String?> = _attachedImageUri.asStateFlow()

    private val _runState = MutableStateFlow<StudioRunState>(StudioRunState.Idle)
    val runState: StateFlow<StudioRunState> = _runState.asStateFlow()

    fun selectTool(tool: AiWorkflowTool) {
        if (_selectedTool.value != tool) {
            _selectedTool.value = tool
            _runState.value = StudioRunState.Idle
            if (!tool.requiresImageOptional) {
                _attachedImageUri.value = null
            }
        }
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun prefillSample() {
        _inputText.value = _selectedTool.value.samplePrompt
    }

    fun attachImage(uriString: String?) {
        _attachedImageUri.value = uriString
    }

    fun clearOutput() {
        _runState.value = StudioRunState.Idle
    }

    fun runWorkflow() {
        val tool = _selectedTool.value
        val prompt = _inputText.value.trim()
        val image = _attachedImageUri.value
        if (prompt.isBlank() && image == null) {
            _runState.value = StudioRunState.Error("Enter a prompt or try the sample input first.")
            return
        }

        viewModelScope.launch {
            _runState.value = StudioRunState.Running
            try {
                val result = aiRepository.runWorkflowTool(
                    tool = tool,
                    prompt = prompt,
                    imageUri = image,
                    responseLength = preferencesState.value.responseLength
                )
                conversationStore.saveStudioResult(result)
                _runState.value = StudioRunState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _runState.value = StudioRunState.Error(
                    e.localizedMessage ?: "Workflow synthesis failed."
                )
            }
        }
    }

    class Factory(
        private val aiRepository: AiRepository,
        private val conversationStore: ConversationStore,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ToolsViewModel(aiRepository, conversationStore, preferencesRepository) as T
        }
    }
}
