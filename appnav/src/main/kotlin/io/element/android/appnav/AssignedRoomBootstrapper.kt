/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import io.element.android.appconfig.ManagedFamilyConfig
import io.element.android.features.invite.api.AcceptInvite
import io.element.android.features.networkmonitor.api.NetworkMonitor
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Opens only the unencrypted room assigned to this account by global account data. */
internal class AssignedRoomBootstrapper(
    private val matrixClient: MatrixClient,
    private val acceptInvite: AcceptInvite,
    private val networkMonitor: NetworkMonitor,
    private val retryDelay: Duration = 5.seconds,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun joinedRooms(): Flow<RoomId> = assignedRooms()
        .flatMapLatest { roomId ->
            if (roomId == null) emptyFlow() else observeAssignedRoom(roomId)
        }
        .distinctUntilChanged()

    private fun assignedRooms(): Flow<RoomId?> = flow {
        while (currentCoroutineContext().isActive) {
            networkMonitor.connectivity.firstConnected()
            val content = matrixClient.getAccountData(ManagedFamilyConfig.ASSIGNED_ROOM_ACCOUNT_DATA_TYPE)
                .getOrNull()
            emit(parseAssignedRoomId(content))
            delay(retryDelay)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAssignedRoom(roomId: RoomId): Flow<RoomId> = combine(
        matrixClient.getRoomInfoFlow(roomId).map { it.orElse(null) },
        networkMonitor.connectivity,
        ::Pair,
    ).transformLatest { (roomInfo, networkStatus) ->
        // Unknown encryption state also fails closed. Sync must confirm plaintext first.
        if (roomInfo?.isEncrypted != false) awaitCancellation()
        when (roomInfo.currentUserMembership) {
            CurrentUserMembership.JOINED -> {
                emit(roomId)
                awaitCancellation()
            }
            CurrentUserMembership.INVITED -> {
                if (networkStatus != NetworkStatus.Connected) awaitCancellation()
                while (currentCoroutineContext().isActive && acceptInvite(roomId).isFailure) {
                    delay(retryDelay)
                }
                // Wait for sync to confirm JOINED before exposing the room.
                awaitCancellation()
            }
            else -> awaitCancellation()
        }
    }

    private suspend fun Flow<NetworkStatus>.firstConnected() {
        this.first { it == NetworkStatus.Connected }
    }
}

internal fun parseAssignedRoomId(content: String?): RoomId? {
    val value = runCatching {
        content
            ?.let(Json::parseToJsonElement)
            ?.jsonObject
            ?.get("room_id")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()
    return value
        ?.takeIf { it.startsWith('!') && it.length > 1 }
        ?.let(::RoomId)
}
