package com.example.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ConversationStore
import com.example.domain.model.ConversationThread
import com.example.domain.model.StudioResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val conversationStore: ConversationStore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val threads: StateFlow<List<ConversationThread>> = combine(
        conversationStore.threads,
        _searchQuery
    ) { list, query ->
        val q = query.trim().lowercase()
        if (q.isEmpty()) list
        else list.filter { thread ->
            thread.title.lowercase().contains(q) ||
                thread.persona.displayName.lowercase().contains(q) ||
                thread.messages.any { it.content.lowercase().contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val studioResults: StateFlow<List<StudioResult>> = combine(
        conversationStore.studioResults,
        _searchQuery
    ) { list, query ->
        val q = query.trim().lowercase()
        if (q.isEmpty()) list
        else list.filter { result ->
            result.tool.title.lowercase().contains(q) ||
                result.inputPrompt.lowercase().contains(q) ||
                result.rawMarkdown.lowercase().contains(q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePin(threadId: String) {
        viewModelScope.launch {
            conversationStore.togglePin(threadId)
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            conversationStore.deleteThread(threadId)
        }
    }

    fun deleteStudioResult(resultId: String) {
        viewModelScope.launch {
            conversationStore.deleteStudioResult(resultId)
        }
    }

    class Factory(
        private val conversationStore: ConversationStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(conversationStore) as T
        }
    }
}
