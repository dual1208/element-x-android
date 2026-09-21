/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.managed

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.ManagedFamilyConfig
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.mapFailure
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.auth.MatrixSession
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

interface ManagedFamilyLoginService {
    suspend fun login(username: String, password: String): Result<MatrixSession>
}

sealed class ManagedFamilyLoginException : Exception() {
    data object InvalidCredentials : ManagedFamilyLoginException()
    data object ReleaseNotAllowed : ManagedFamilyLoginException()
    data object TemporarilyUnavailable : ManagedFamilyLoginException()
    data object ConnectionFailed : ManagedFamilyLoginException()
    data object InvalidResponse : ManagedFamilyLoginException()
    data object MissingClientCertificate : ManagedFamilyLoginException()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultManagedFamilyLoginService(
    @ApplicationContext private val context: Context,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val jsonProvider: JsonProvider,
) : ManagedFamilyLoginService {
    private val client by lazy { createClient() }

    override suspend fun login(username: String, password: String): Result<MatrixSession> =
        withContext(coroutineDispatchers.io) {
            runCatchingExceptions {
                val normalizedUsername = username.trim()
                if (normalizedUsername.isEmpty() || normalizedUsername.startsWith("@") || ':' in normalizedUsername) {
                    throw ManagedFamilyLoginException.InvalidCredentials
                }
                val json = jsonProvider()
                val body = json.encodeToString(
                    LoginRequest(
                        username = normalizedUsername,
                        password = password,
                        initialDeviceDisplayName = "家庭聊天 Android",
                    )
                ).toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder()
                    .url(LOGIN_URL)
                    .post(body)
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val responseJson = response.body.byteStream().use(::readBoundedUtf8)
                        if (!response.isSuccessful) {
                            throw mapHttpFailure(response.code, responseJson)
                        }
                        val payload = runCatching { json.decodeFromString<LoginResponse>(responseJson) }
                            .getOrElse { throw ManagedFamilyLoginException.InvalidResponse }
                        val expectedUserId = "@$normalizedUsername:${ManagedFamilyConfig.HOMESERVER_NAME}"
                        if (payload.accessToken.isBlank() || payload.userId != expectedUserId || payload.deviceId.isBlank()) {
                            throw ManagedFamilyLoginException.InvalidResponse
                        }
                        MatrixSession(
                            accessToken = payload.accessToken,
                            userId = payload.userId,
                            deviceId = payload.deviceId,
                        )
                    }
                } catch (_: SSLHandshakeException) {
                    throw ManagedFamilyLoginException.ConnectionFailed
                } catch (failure: IOException) {
                    throw ManagedFamilyLoginException.ConnectionFailed
                }
            }.mapFailure { failure ->
                failure as? ManagedFamilyLoginException ?: ManagedFamilyLoginException.ConnectionFailed
            }
        }

    private fun createClient(): OkHttpClient = try {
        createMtlsClient()
    } catch (failure: ManagedFamilyLoginException) {
        throw failure
    } catch (_: IOException) {
        throw ManagedFamilyLoginException.MissingClientCertificate
    } catch (_: GeneralSecurityException) {
        throw ManagedFamilyLoginException.ReleaseNotAllowed
    }

    private fun createMtlsClient(): OkHttpClient {
        val password = CharArray(0)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            context.assets.open(CLIENT_CERTIFICATE_ASSET).use { load(it, password) }
        }
        val aliases = keyStore.aliases()
        var hasUsableIdentity = false
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (keyStore.isKeyEntry(alias)) {
                val chain = keyStore.getCertificateChain(alias)?.filterIsInstance<X509Certificate>().orEmpty()
                if (chain.isNotEmpty()) {
                    chain.forEach(X509Certificate::checkValidity)
                    hasUsableIdentity = true
                }
            }
        }
        if (!hasUsableIdentity) throw ManagedFamilyLoginException.MissingClientCertificate

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        val trustManager = defaultTrustManager()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, arrayOf(trustManager), null)
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun defaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }
        return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    }

    private fun mapHttpFailure(status: Int, body: String): ManagedFamilyLoginException {
        val errorCode = runCatching { jsonProvider().decodeFromString<ErrorResponse>(body).errorCode }.getOrNull()
        return when {
            status == 403 && errorCode == RELEASE_NOT_ALLOWED -> ManagedFamilyLoginException.ReleaseNotAllowed
            status == 403 && errorCode == FORBIDDEN -> ManagedFamilyLoginException.InvalidCredentials
            status == 503 -> ManagedFamilyLoginException.TemporarilyUnavailable
            else -> ManagedFamilyLoginException.InvalidResponse
        }
    }

    private fun readBoundedUtf8(input: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > MAX_RESPONSE_BYTES) throw ManagedFamilyLoginException.InvalidResponse
            output.write(buffer, 0, count)
        }
        return output.toByteArray().decodeToString()
    }

    @Serializable
    private data class LoginRequest(
        val username: String,
        val password: String,
        @SerialName("initial_device_display_name") val initialDeviceDisplayName: String,
    )

    @Serializable
    private data class LoginResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("user_id") val userId: String,
        @SerialName("device_id") val deviceId: String,
    )

    @Serializable
    private data class ErrorResponse(
        @SerialName("errcode") val errorCode: String? = null,
    )

    private companion object {
        const val CLIENT_CERTIFICATE_ASSET = "family-client.p12"
        const val FORBIDDEN = "M_FORBIDDEN"
        const val RELEASE_NOT_ALLOWED = "M_RELEASE_NOT_ALLOWED"
        const val LOGIN_URL = "${ManagedFamilyConfig.HOMESERVER_URL}/client-auth/login"
        const val MAX_RESPONSE_BYTES = 64 * 1024
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
