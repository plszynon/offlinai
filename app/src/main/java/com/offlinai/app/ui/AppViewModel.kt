package com.offlinai.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.offlinai.app.data.*
import com.offlinai.app.engine.ChatTurn
import com.offlinai.app.engine.LlamaEngine
import com.offlinai.app.engine.Templates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { Chat, Library, Installed }

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = Room.databaseBuilder(app, AppDatabase::class.java, "offlinai.db").build().dao()
    private val prefs = app.getSharedPreferences("offlinai", Context.MODE_PRIVATE)
    val repo = ModelRepository(app)
    private val engine = LlamaEngine(app.contentResolver)
    private var genJob: Job? = null

    val screen = MutableStateFlow(Screen.Chat)
    val currentChatId = MutableStateFlow<Long?>(null)
    val installed = MutableStateFlow(repo.installed())
    val activeModel = MutableStateFlow(prefs.getString("active", null))
    val downloading = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow(0f)
    val modelLoading = MutableStateFlow(false)
    val generating = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    val searchResults = MutableStateFlow<List<HfRepo>>(emptyList())
    val searching = MutableStateFlow(false)
    val searched = MutableStateFlow(false)
    val expandedRepo = MutableStateFlow<String?>(null)
    val repoFiles = MutableStateFlow<Map<String, List<HfFile>>>(emptyMap())

    val chats = dao.chats().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val messages = currentChatId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.messages(id) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        val a = activeModel.value
        if (a != null && installed.value.none { it.name == a }) clearActive()
    }

    private fun clearActive() { activeModel.value = null; prefs.edit().remove("active").apply() }

    fun refreshInstalled() { installed.value = repo.installed() }

    fun newChat() { currentChatId.value = null }
    fun openChat(id: Long) { currentChatId.value = id }

    fun deleteChat(id: Long) = viewModelScope.launch {
        dao.deleteMessages(id); dao.deleteChat(id)
        if (currentChatId.value == id) currentChatId.value = null
    }

    // ---- models ----
    fun selectModel(name: String) {
        activeModel.value = name
        prefs.edit().putString("active", name).apply()
        error.value = null
        viewModelScope.launch {
            modelLoading.value = true
            try { engine.load(File(repo.dir, name)) }
            catch (e: Exception) { error.value = "Couldn't load model: ${e.message}" }
            finally { modelLoading.value = false }
        }
    }

    fun uninstall(name: String) = viewModelScope.launch {
        if (activeModel.value == name) { engine.unload(); clearActive() }
        repo.delete(name)
        refreshInstalled()
    }

    fun install(url: String, fileName: String) {
        if (downloading.value != null || fileName.isBlank()) return
        error.value = null
        downloading.value = fileName; progress.value = 0f
        viewModelScope.launch {
            try {
                repo.download(url, fileName) { progress.value = it }
                refreshInstalled()
                if (activeModel.value == null) selectModel(fileName)
            } catch (e: Exception) {
                error.value = e.message ?: "Download failed"
            } finally { downloading.value = null }
        }
    }

    // ---- Hugging Face search ----
    fun search(q: String) {
        val query = q.trim()
        if (query.isEmpty()) { searched.value = false; return }
        viewModelScope.launch {
            searching.value = true; error.value = null; expandedRepo.value = null
            try { searchResults.value = repo.search(query); searched.value = true }
            catch (e: Exception) { error.value = "Search failed. Check your internet connection." }
            finally { searching.value = false }
        }
    }

    fun clearSearch() { searched.value = false; expandedRepo.value = null }

    fun toggleRepo(id: String) {
        if (expandedRepo.value == id) { expandedRepo.value = null; return }
        expandedRepo.value = id
        if (repoFiles.value[id] == null) viewModelScope.launch {
            val list = try { repo.files(id) } catch (e: Exception) { error.value = "Couldn't load files for $id"; emptyList() }
            repoFiles.value = repoFiles.value + (id to list)
        }
    }

    // ---- chat ----
    fun stop() { genJob?.cancel() }

    fun send(text: String) {
        if (text.isBlank() || generating.value) return
        val name = activeModel.value
        if (name == null) { error.value = "Install and select a model first (menu, then Model library)"; return }
        error.value = null
        genJob = viewModelScope.launch {
            generating.value = true
            try {
                val now = System.currentTimeMillis()
                val chatId: Long = currentChatId.value
                    ?: dao.insertChat(ChatEntity(title = text.take(40), createdAt = now)).also { currentChatId.value = it }
                dao.insertMessage(MessageEntity(chatId = chatId, role = "user", content = text, createdAt = now))
                val history = dao.messagesOnce(chatId).map { ChatTurn(it.role, it.content) }
                val aId = dao.insertMessage(MessageEntity(chatId = chatId, role = "assistant", content = "", createdAt = now))

                modelLoading.value = true
                try { engine.load(File(repo.dir, name)) } finally { modelLoading.value = false }

                val sb = StringBuilder()
                try {
                    engine.generate(Templates.build(name, history))
                        .takeWhile { sb.append(it); !Templates.hasStop(sb) }
                        .collect { dao.updateMessage(MessageEntity(aId, chatId, "assistant", sb.toString(), now)) }
                } finally {
                    withContext(NonCancellable) {
                        dao.updateMessage(MessageEntity(aId, chatId, "assistant", Templates.cutStop(sb.toString()).trim(), now))
                    }
                }
            } catch (e: CancellationException) {
                // stopped by the user
            } catch (e: Exception) {
                error.value = e.message ?: "Generation failed"
            } finally { generating.value = false }
        }
    }
}
