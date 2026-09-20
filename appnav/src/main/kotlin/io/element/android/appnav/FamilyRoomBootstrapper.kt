/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import io.element.android.features.invite.api.AcceptInvite
import io.element.android.features.networkmonitor.api.NetworkMonitor
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Waits for sync to expose the configured room, accepting only that room's invitation. */
internal class FamilyRoomBootstrapper(
    private val matrixClient: MatrixClient,
    private val acceptInvite: AcceptInvite,
    private val networkMonitor: NetworkMonitor,
    private val retryDelay: Duration = 5.seconds,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun awaitJoined(roomId: RoomId): RoomId = combine(
        matrixClient.getRoomInfoFlow(roomId),
        networkMonitor.connectivity,
    ) { roomInfo, networkStatus ->
        roomInfo.orElse(null)?.currentUserMembership to networkStatus
    }.flatMapLatest { (membership, networkStatus) ->
        flow {
            when {
                membership == CurrentUserMembership.JOINED -> emit(roomId)
                membership == CurrentUserMembership.INVITED && networkStatus == NetworkStatus.Connected -> {
                    while (true) {
                        if (acceptInvite(roomId).isSuccess) {
                            emit(roomId)
                            break
                        }
                        delay(retryDelay)
                    }
                }
                else -> awaitCancellation()
            }
        }
    }.first()
}
