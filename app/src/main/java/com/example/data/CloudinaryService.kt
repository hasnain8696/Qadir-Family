package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CloudinaryUploadResult(
    val publicId: String,
    val secureUrl: String,
    val url: String,
    val format: String,
    val bytes: Long,
    val resourceType: String
)

class CloudinaryService(private val context: Context) {

    // Credentials with fallback to user's provided values
    val cloudName: String = try {
        BuildConfig.CLOUDINARY_CLOUD_NAME.ifBlank { DEFAULT_CLOUD_NAME }
    } catch (e: Throwable) {
        DEFAULT_CLOUD_NAME
    }

    val apiKey: String = try {
        BuildConfig.CLOUDINARY_API_KEY.ifBlank { DEFAULT_API_KEY }
    } catch (e: Throwable) {
        DEFAULT_API_KEY
    }

    val apiSecret: String = try {
        BuildConfig.CLOUDINARY_API_SECRET
    } catch (e: Throwable) {
        ""
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Upload document or media bytes to Cloudinary storage.
     */
    suspend fun uploadBytes(
        bytes: ByteArray,
        fileName: String,
        folder: String = "qadir_family/documents"
    ): Result<CloudinaryUploadResult> = withContext(Dispatchers.IO) {
        try {
            val timestamp = (System.currentTimeMillis() / 1000).toString()

            val ext = fileName.substringAfterLast(".", "pdf").lowercase(Locale.ROOT)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: if (ext == "pdf") "application/pdf" else "application/octet-stream"

            val fileRequestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)

            if (apiSecret.isNotBlank()) {
                val signParams = sortedMapOf(
                    "folder" to folder,
                    "timestamp" to timestamp
                )
                val signature = generateSha1Signature(signParams, apiSecret)
                multipartBuilder
                    .addFormDataPart("api_key", apiKey)
                    .addFormDataPart("timestamp", timestamp)
                    .addFormDataPart("folder", folder)
                    .addFormDataPart("signature", signature)
            } else {
                multipartBuilder
                    .addFormDataPart("upload_preset", "qadir_preset")
                    .addFormDataPart("folder", folder)
            }

            val multipartBody = multipartBuilder
                .addFormDataPart("file", fileName, fileRequestBody)
                .build()

            val uploadUrl = "https://api.cloudinary.com/v1_1/$cloudName/auto/upload"

            val request = Request.Builder()
                .url(uploadUrl)
                .post(multipartBody)
                .build()

            Log.d(TAG, "Uploading $fileName (${bytes.size} bytes) to Cloudinary ($cloudName)...")

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message")
                        ?: "HTTP ${response.code}: $responseBody"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                Log.e(TAG, "Cloudinary upload failed: $errorMsg")
                return@withContext Result.failure(Exception("Cloudinary upload failed: $errorMsg"))
            }

            val json = JSONObject(responseBody)
            val result = CloudinaryUploadResult(
                publicId = json.optString("public_id", ""),
                secureUrl = json.optString("secure_url", ""),
                url = json.optString("url", ""),
                format = json.optString("format", ext),
                bytes = json.optLong("bytes", bytes.size.toLong()),
                resourceType = json.optString("resource_type", "auto")
            )

            Log.i(TAG, "Cloudinary upload success! Secure URL: ${result.secureUrl}")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Cloudinary upload exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Upload from an Android content Uri (e.g. from File Picker).
     */
    suspend fun uploadFromUri(
        uri: Uri,
        fileName: String,
        folder: String = "qadir_family/documents"
    ): Result<CloudinaryUploadResult> = withContext(Dispatchers.IO) {
        try {
            val bytes = readBytesFromUri(uri)
                ?: return@withContext Result.failure(Exception("Could not read file from URI: $uri"))

            uploadBytes(bytes, fileName, folder)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readBytesFromUri(uri: Uri): ByteArray? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading bytes from uri: $uri, error: ${e.message}")
            null
        }
    }

    private fun generateSha1Signature(params: Map<String, String>, secret: String): String {
        val sortedQuery = params.toSortedMap()
            .map { "${it.key}=${it.value}" }
            .joinToString("&")
        val toSign = "$sortedQuery$secret"
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(toSign.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "CloudinaryService"
        const val DEFAULT_CLOUD_NAME = "zway580k"
        const val DEFAULT_API_KEY = "744951532476378"

        @Volatile
        private var instance: CloudinaryService? = null

        fun getInstance(context: Context): CloudinaryService {
            return instance ?: synchronized(this) {
                instance ?: CloudinaryService(context.applicationContext).also { instance = it }
            }
        }
    }
}
