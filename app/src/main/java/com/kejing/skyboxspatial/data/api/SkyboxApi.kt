package com.kejing.skyboxspatial.data.api

import com.kejing.skyboxspatial.domain.model.JobStatus
import com.kejing.skyboxspatial.domain.model.SkyboxJob
import com.kejing.skyboxspatial.domain.model.SkyboxStyle
import java.io.File
import java.io.IOException

/** The service refused or failed the request; [code] is the HTTP status. */
class ApiException(val code: Int, message: String) : Exception(message)

/** The request was sent but no usable answer came back, so the server may or may not have a task. */
class UnknownSubmitException(cause: IOException) : IOException(cause.message, cause)

/** Thin client for the generation service. Every call is a suspend function that does its own I/O dispatch. */
interface SkyboxApi {
    @Throws(ApiException::class, IOException::class)
    suspend fun styles(apiKey: String): List<SkyboxStyle>

    @Throws(ApiException::class, UnknownSubmitException::class)
    suspend fun generate(apiKey: String, prompt: String, styleId: Int, remixImagineId: Long?): SkyboxJob

    @Throws(ApiException::class, IOException::class)
    suspend fun job(apiKey: String, id: Long): SkyboxJob

    /** Most recent tasks of this key, newest first. Used to recover an unconfirmed submission. */
    @Throws(ApiException::class, IOException::class)
    suspend fun recentJobs(apiKey: String, limit: Int): List<SkyboxJob>

    /** Streams [url] into [destination]; the file only exists once it is complete. */
    @Throws(IOException::class)
    suspend fun download(url: String, destination: File, onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> })
}

/** Mapping shared by the real client and tests. */
internal fun jobStatusOf(raw: String?): JobStatus = JobStatus.from(raw)
