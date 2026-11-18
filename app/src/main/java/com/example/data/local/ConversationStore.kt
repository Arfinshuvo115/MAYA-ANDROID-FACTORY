package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.domain.model.ConversationThread
import com.example.domain.model.StudioResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ConversationStore(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val threadsFile = File(appContext.filesDir, "maya_conversations.json")
    private val studioFile = File(appContext.filesDir, "maya_studio_results.json")

    private val _threads = MutableStateFlow<List<ConversationThread>>(emptyList())
    val threads: StateFlow<List<ConversationThread>> = _threads.asStateFlow()

    private val _studioResults = MutableStateFlow<List<StudioResult>>(emptyList())
    val studioResults: StateFlow<List<StudioResult>> = _studioResults.asStateFlow()

    init {
        scope.launch {
            loadAll()
        }
    }

    private suspend fun loadAll() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (threadsFile.exists()) {
                runCatching {
                    val raw = threadsFile.readText()
                    json.decodeFromString<List<ConversationThread>>(raw)
                }.onSuccess { loaded ->
                    _threads.value = loaded.sortedWith(
                        compareByDescending<ConversationThread> { it.isPinned }
                            .thenByDescending { it.updatedAtMillis }
                    )
                }.onFailure { err ->
                    Log.e(TAG, "Failed to read saved conversations", err)
                }
            }
            if (studioFile.exists()) {
                runCatching {
                    val raw = studioFile.readText()
                    json.decodeFromString<List<StudioResult>>(raw)
                }.onSuccess { loaded ->
                    _studioResults.value = loaded.sortedByDescending { it.timestampMillis }
                }.onFailure { err ->
                    Log.e(TAG, "Failed to read studio results", err)
                }
            }
        }
    }

    suspend fun upsertThread(thread: ConversationThread) = mutex.withLock {
        val updated = (_threads.value.filterNot { it.id == thread.id } + thread)
            .sortedWith(
                compareByDescending<ConversationThread> { it.isPinned }
                    .thenByDescending { it.updatedAtMillis }
            )
        _threads.value = updated
        persistThreads(updated)
    }

    suspend fun togglePin(threadId: String) = mutex.withLock {
        val updated = _threads.value.map { thread ->
            if (thread.id == threadId) thread.copy(isPinned = !thread.isPinned) else thread
        }.sortedWith(
            compareByDescending<ConversationThread> { it.isPinned }
                .thenByDescending { it.updatedAtMillis }
        )
        _threads.value = updated
        persistThreads(updated)
    }

    suspend fun deleteThread(threadId: String) = mutex.withLock {
        val updated = _threads.value.filterNot { it.id == threadId }
        _threads.value = updated
        persistThreads(updated)
    }

    suspend fun saveStudioResult(result: StudioResult) = mutex.withLock {
        val updated = (listOf(result) + _studioResults.value.filterNot { it.id == result.id })
            .take(50)
        _studioResults.value = updated
        persistStudio(updated)
    }

    suspend fun deleteStudioResult(resultId: String) = mutex.withLock {
        val updated = _studioResults.value.filterNot { it.id == resultId }
        _studioResults.value = updated
        persistStudio(updated)
    }

    suspend fun clearAll() = mutex.withLock {
        _threads.value = emptyList()
        _studioResults.value = emptyList()
        withContext(Dispatchers.IO) {
            if (threadsFile.exists()) threadsFile.delete()
            if (studioFile.exists()) studioFile.delete()
        }
    }

    private suspend fun persistThreads(list: List<ConversationThread>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(appContext.filesDir, "maya_conversations.tmp")
                tmp.writeText(json.encodeToString(list))
                tmp.renameTo(threadsFile)
            }.onFailure { err ->
                Log.e(TAG, "Failed to persist conversations", err)
            }
        }
    }

    private suspend fun persistStudio(list: List<StudioResult>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(appContext.filesDir, "maya_studio_results.tmp")
                tmp.writeText(json.encodeToString(list))
                tmp.renameTo(studioFile)
            }.onFailure { err ->
                Log.e(TAG, "Failed to persist studio results", err)
            }
        }
    }

    private companion object {
        const val TAG = "ConversationStore"
    }
}
