package com.offlinai.app.engine

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ChatTurn(val role: String, val content: String)

/** Runs GGUF models on the phone with llama.cpp (Kotlin-LlamaCpp). No server, no Termux. */
class LlamaEngine(private val contentResolver: ContentResolver) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val events = MutableSharedFlow<LlamaHelper.LLMEvent>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var helper: LlamaHelper? = null
    private var loadedPath: String? = null
    private val lock = Mutex()

    suspend fun load(file: File) = lock.withLock {
        if (loadedPath == file.absolutePath) return@withLock
        releaseLocked()
        val h = LlamaHelper(contentResolver = contentResolver, scope = scope, sharedFlow = events)
        withTimeout(180_000) {
            suspendCancellableCoroutine<Unit> { cont ->
                try {
                    h.load(path = Uri.fromFile(file).toString(), contextLength = 4096) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        }
        helper = h
        loadedPath = file.absolutePath
    }

    suspend fun unload() = lock.withLock { releaseLocked() }

    private fun releaseLocked() {
        helper?.let { runCatching { it.abort() }; runCatching { it.release() } }
        helper = null
        loadedPath = null
    }

    /** Streams generated text pieces for a fully formatted prompt. Cancelling the flow stops generation. */
    fun generate(prompt: String): Flow<String> = callbackFlow {
        val h = helper ?: throw IllegalStateException("Model is not loaded")
        var finished = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { e ->
                when (e) {
                    is LlamaHelper.LLMEvent.Ongoing -> trySend(e.word)
                    is LlamaHelper.LLMEvent.Done -> {
                        finished = true
                        runCatching { h.stopPrediction() }
                        close()
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        finished = true
                        runCatching { h.stopPrediction() }
                        close(IllegalStateException(e.message ?: "Generation failed"))
                    }
                    else -> {}
                }
            }
        }
        h.predict(prompt)
        awaitClose {
            if (!finished) runCatching { h.abort() }
            job.cancel()
        }
    }
}

/** Chat prompt formats. The engine takes a raw prompt, so we build it per model family. */
object Templates {
    private val stops = listOf("<|im_end|>", "<|eot_id|>", "<end_of_turn>", "<|end|>", "</s>", "<|endoftext|>", "<|im_start|>")

    fun hasStop(s: CharSequence) = stops.any { s.contains(it) }

    fun cutStop(s: String): String {
        var end = s.length
        for (t in stops) { val i = s.indexOf(t); if (i in 0 until end) end = i }
        return s.substring(0, end)
    }

    fun build(modelFile: String, history: List<ChatTurn>): String {
        val turns = trim(history)
        val n = modelFile.lowercase()
        return when {
            "llama-3" in n || "llama3" in n || "llama_3" in n -> llama3(turns)
            "gemma" in n -> gemma(turns)
            "mistral" in n || "mixtral" in n -> mistral(turns)
            "phi-3" in n || "phi3" in n -> phi3(turns)
            "tinyllama" in n || "zephyr" in n -> zephyr(turns)
            else -> chatml(turns)
        }
    }

    /** Keep the newest turns that fit in roughly 1500-2000 tokens. */
    private fun trim(h: List<ChatTurn>): List<ChatTurn> {
        var total = 0
        val out = ArrayDeque<ChatTurn>()
        for (t in h.asReversed()) {
            if (out.isNotEmpty() && total + t.content.length > 7000) break
            out.addFirst(t); total += t.content.length
        }
        return out.toList()
    }

    private fun chatml(t: List<ChatTurn>) =
        t.joinToString("") { "<|im_start|>${it.role}\n${it.content}<|im_end|>\n" } + "<|im_start|>assistant\n"

    private fun llama3(t: List<ChatTurn>) =
        t.joinToString("") { "<|start_header_id|>${it.role}<|end_header_id|>\n\n${it.content}<|eot_id|>" } +
            "<|start_header_id|>assistant<|end_header_id|>\n\n"

    private fun gemma(t: List<ChatTurn>) =
        t.joinToString("") { "<start_of_turn>${if (it.role == "user") "user" else "model"}\n${it.content}<end_of_turn>\n" } +
            "<start_of_turn>model\n"

    private fun mistral(t: List<ChatTurn>) =
        t.joinToString("") { if (it.role == "user") "[INST] ${it.content} [/INST]" else " ${it.content}</s>" }

    private fun phi3(t: List<ChatTurn>) =
        t.joinToString("") { "<|${it.role}|>\n${it.content}<|end|>\n" } + "<|assistant|>\n"

    private fun zephyr(t: List<ChatTurn>) =
        t.joinToString("") { "<|${it.role}|>\n${it.content}</s>\n" } + "<|assistant|>\n"
}
