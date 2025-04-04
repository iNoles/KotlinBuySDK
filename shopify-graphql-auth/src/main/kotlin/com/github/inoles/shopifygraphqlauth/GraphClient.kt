package com.github.inoles.shopifygraphqlauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.File
import java.io.IOException
import kotlin.math.pow
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class GraphClient private constructor(
    internal val serverUrl: HttpUrl,
    internal val httpCallFactory: Call.Factory,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun buildRequest(requestBody: RequestBody): Request {
        return Request.Builder().apply {
            url(serverUrl)
            cacheControl(CacheControl.Builder().maxAge(1L.hours).build())
            post(requestBody)
        }.build()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun executeQuery(query: String, variables: Map<String, Any>? = null): JsonObject? {
        val requestBody = createRequestBody(query, variables)
        val request = buildRequest(requestBody)

        return httpCallFactory.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                handleResponse(response)
            }
        }
    }

    private fun handleResponse(response: Response): JsonObject? {
        if (!response.isSuccessful) {
            throw IOException("Unexpected response: ${response.code} - ${response.message}")
        }
        return json.parseToJsonElement(response.body.string()).jsonObject
    }

    private fun createRequestBody(query: String, variables: Map<String, Any>?): RequestBody {
        val jsonPayload = buildJsonPayload(query, variables)
        return jsonPayload.toRequestBody("application/json".toMediaType())
    }

    fun buildJsonPayload(query: String, variables: Map<String, Any>?): String {
        val queryJson = Json.encodeToJsonElement(String.serializer(), query)

        val variablesJson = variables?.let {
            // Convert the values to JsonElement manually
            val serializedVariables = it.mapValues { entry ->
                when (val value = entry.value) {
                    is String -> JsonPrimitive(value)  // Handle String
                    is Number -> JsonPrimitive(value)  // Handle Numbers
                    is Boolean -> JsonPrimitive(value) // Handle Booleans
                    is JsonElement -> value           // If it's already a JsonElement, just use it
                    is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }.mapValues { buildJsonElement(it.value) }) // Handle Map
                    is List<*> -> JsonArray(value.map { buildJsonElement(it) }) // Handle List
                    else -> JsonNull                   // Handle any unknown types as null
                }
            }

            Json.encodeToJsonElement(MapSerializer(String.serializer(), JsonElement.serializer()), serializedVariables)
        } ?: JsonObject(emptyMap())

        val jsonPayload = JsonObject(mapOf(
            "query" to queryJson,
            "variables" to variablesJson
        ))

        return jsonPayload.toString()
    }

    // Recursively build JsonElement for complex structures like List or Map
    private fun buildJsonElement(value: Any?): JsonElement {
        return when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }.mapValues { buildJsonElement(it.value) })
            is List<*> -> JsonArray(value.map { buildJsonElement(it) })
            else -> JsonNull
        }
    }

    class Builder(
        private var shopDomain: String,
        private var accessToken: String,
        private var apiVersion: String,
        private var cacheDir: File? = null, // Require cache directory
        private var httpClient: OkHttpClient? = null,
        private var maxRetries: Int = 3,
    ) {
        init {
            require(shopDomain.isNotBlank()) { "Shop URL must be provided" }
            require(accessToken.isNotBlank()) { "Access token must be provided" }
            require(apiVersion.isNotBlank()) { "API version must be provided" }
        }

        fun shopDomain(shopUrl: String) = apply { this.shopDomain = shopUrl }
        fun accessToken(accessToken: String) = apply { this.accessToken = accessToken }
        fun apiVersion(apiVersion: String) = apply { this.apiVersion = apiVersion }
        fun cacheDir(cacheDir: File) = apply { this.cacheDir = cacheDir }
        fun httpClient(httpClient: OkHttpClient) = apply { this.httpClient = httpClient }
        fun maxRetries(maxRetries: Int) = apply { this.maxRetries = maxRetries }

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder().apply {
                cacheDir?.let { cache(Cache(it, 10L * 1024 * 1024)) } // 10MB cache if set
                connectTimeout(30.seconds)
                readTimeout(30.seconds )
                writeTimeout(30.seconds)
            }.build()
        }

        private fun authInterceptor(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val builder = original.newBuilder()
                .method(original.method, original.body)
                .header("X-SDK-Variant", "android")
                .header("X-Shopify-Storefront-Access-Token", accessToken)
            return chain.proceed(builder.build())
        }

        private fun retryInterceptor(chain: Interceptor.Chain): Response {
            var attempt = 0
            var lastException: IOException? = null

            while (attempt < maxRetries) {
                try {
                    val response = chain.proceed(chain.request())

                    // Don't retry on client errors (4xx), only retry on server errors (5xx)
                    if (response.isSuccessful || response.code in 400..499) {
                        return response
                    }

                    lastException = IOException("Unsuccessful response: ${response.code}")
                } catch (e: IOException) {
                    lastException = e
                }

                attempt++
                if (attempt < maxRetries) {
                    val delayTime = (2.0.pow(attempt) * 100).toLong()
                    Thread.sleep(delayTime)
                }
            }

            throw lastException ?: IOException("Failed after $maxRetries retries")
        }

        fun build(): GraphClient {
            // Use custom httpClient if provided, otherwise default one
            val okHttpClient = (httpClient ?: defaultOkHttpClient()).newBuilder().apply {
                addInterceptor(::retryInterceptor)  // Ensure retryInterceptor is added in custom client
                addInterceptor(::authInterceptor)  // Ensure authInterceptor is added in custom client
            }.build()
            val serverUrl = HttpUrl.Builder()
                .scheme("https")
                .host(shopDomain)
                .addPathSegments("api/$apiVersion/graphql")
                .build()
            return GraphClient(serverUrl, okHttpClient)
        }
    }
}

