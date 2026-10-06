package com.kejing.skyboxspatial.data.api

import com.kejing.skyboxspatial.domain.model.SkyboxJob
import com.kejing.skyboxspatial.domain.model.SkyboxStyle
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Skybox AI (Blockade Labs) REST client over HttpURLConnection and org.json, so the app needs no
 * extra dependencies. Endpoints per https://api-documentation.blockadelabs.com/api/skybox.html.
 */
class BlockadeSkyboxApi(
    private val baseUrl: String = "https://backend.blockadelabs.com/api/v1",
) : SkyboxApi {

    override suspend fun styles(apiKey: String): List<SkyboxStyle> =
        withContext(Dispatchers.IO) {
            val text = request("GET", "$baseUrl/skybox/styles", apiKey, body = null)
            parseStyles(text)
        }

    override suspend fun generate(apiKey: String, prompt: String, styleId: Int, remixImagineId: Long?): SkyboxJob =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("skybox_style_id", styleId).put("prompt", prompt)
            if (remixImagineId != null) body.put("remix_imagine_id", remixImagineId)
            val text =
                try {
                    request("POST", "$baseUrl/skybox", apiKey, body.toString())
                } catch (e: IOException) {
                    // The POST may have reached the server; the caller must look before resubmitting.
                    throw UnknownSubmitException(e)
                }
            parseJob(JSONObject(text))
        }

    override suspend fun job(apiKey: String, id: Long): SkyboxJob =
        withContext(Dispatchers.IO) {
            val root = JSONObject(request("GET", "$baseUrl/imagine/requests/$id", apiKey, null))
            parseJob(root.optJSONObject("request") ?: root.optJSONObject("response") ?: root)
        }

    override suspend fun recentJobs(apiKey: String, limit: Int): List<SkyboxJob> =
        withContext(Dispatchers.IO) {
            val root = JSONObject(request("GET", "$baseUrl/imagine/myRequests?limit=$limit&order=DESC", apiKey, null))
            val data = root.optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { index -> data.optJSONObject(index)?.let { parseJob(it) } }
        }

    override suspend fun download(url: String, destination: File, onProgress: (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val partial = File(destination.parentFile, destination.name + ".part")
            partial.parentFile?.mkdirs()
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = DOWNLOAD_READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode} while downloading")
                val total = connection.contentLengthLong
                var copied = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress(copied, total)
                        }
                        output.fd.sync()
                    }
                }
                if (total > 0 && copied != total) throw IOException("Download ended early: $copied of $total bytes")
                if (destination.exists()) destination.delete()
                if (!partial.renameTo(destination)) throw IOException("Could not move the downloaded file into place")
            } catch (e: IOException) {
                partial.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }

    private fun request(method: String, url: String, apiKey: String, body: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(code, errorMessage(text, code))
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(text: String, code: Int): String {
        val parsed = runCatching { JSONObject(text) }.getOrNull()
        val message = parsed?.optString("error")?.takeIf { it.isNotBlank() }
            ?: parsed?.optString("message")?.takeIf { it.isNotBlank() }
        return message ?: text.take(200).ifBlank { "HTTP $code" }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 60_000

        fun parseStyles(text: String): List<SkyboxStyle> {
            val array = JSONArray(text)
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                SkyboxStyle(
                    id = item.optInt("id"),
                    name = item.optString("name"),
                    modelVersion = item.optString("model_version").ifBlank { item.optString("model").filter { it.isDigit() } },
                    maxChars = item.optInt("max-char", 0),
                )
            }
        }

        fun parseJob(item: JSONObject): SkyboxJob =
            SkyboxJob(
                id = item.optLong("id"),
                obfuscatedId = item.optString("obfuscated_id"),
                status = jobStatusOf(item.optString("status")),
                prompt = item.optString("prompt").ifBlank { item.optJSONObject("generator_data")?.optString("prompt").orEmpty() },
                styleId = item.optInt("skybox_style_id").takeIf { item.has("skybox_style_id") },
                styleName = item.optString("skybox_style_name").takeIf { it.isNotBlank() },
                model = item.optString("model").takeIf { it.isNotBlank() },
                fileUrl = item.optString("file_url").takeIf { it.isNotBlank() },
                thumbUrl = item.optString("thumb_url").takeIf { it.isNotBlank() },
                errorMessage = item.optString("error_message").takeIf { it.isNotBlank() && it != "null" },
                queuePosition = item.optInt("queue_position").takeIf { item.has("queue_position") },
            )
    }
}
