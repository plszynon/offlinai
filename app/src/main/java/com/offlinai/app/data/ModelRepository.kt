package com.offlinai.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

data class HfRepo(val id: String, val downloads: Long)
data class HfFile(val path: String, val bytes: Long)

/** Stores GGUF models in filesDir/models and talks to the Hugging Face API. */
class ModelRepository(context: Context) {
    val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun installed(): List<File> =
        dir.listFiles { f -> f.extension == "gguf" }?.sortedBy { it.name } ?: emptyList()

    fun delete(name: String) = File(dir, name).delete()

    fun url(repo: String, file: String) = "https://huggingface.co/$repo/resolve/main/$file"

    suspend fun search(query: String): List<HfRepo> = withContext(Dispatchers.IO) {
        val url = "https://huggingface.co/api/models".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("filter", "gguf")
            .addQueryParameter("sort", "downloads")
            .addQueryParameter("direction", "-1")
            .addQueryParameter("limit", "30")
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            check(r.isSuccessful) { "Search failed (HTTP ${r.code})" }
            val arr = JSONArray(r.body!!.string())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HfRepo(o.getString("id"), o.optLong("downloads"))
            }
        }
    }

    /** Top-level .gguf files in a repo (skips vision projectors and split shards). */
    suspend fun files(repo: String): List<HfFile> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://huggingface.co/api/models/$repo/tree/main").build()
        http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { "Couldn't list files (HTTP ${r.code})" }
            val arr = JSONArray(r.body!!.string())
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optString("type") == "file" }
                .map { HfFile(it.getString("path"), it.optLong("size")) }
                .filter { it.path.endsWith(".gguf") && '/' !in it.path &&
                    "mmproj" !in it.path.lowercase() && "-of-0" !in it.path }
                .sortedBy { it.bytes }
        }
    }

    suspend fun download(url: String, fileName: String, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val target = File(dir, fileName)
            val tmp = File(dir, "$fileName.part")
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    check(r.isSuccessful) { "Download failed (HTTP ${r.code})" }
                    val body = r.body!!
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            var last = 0f
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val p = read.toFloat() / total
                                    if (p - last >= 0.01f) { onProgress(p); last = p }
                                }
                            }
                        }
                    }
                }
                check(tmp.renameTo(target)) { "Couldn't save the model file" }
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
}
