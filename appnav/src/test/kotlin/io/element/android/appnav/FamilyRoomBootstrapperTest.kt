/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.ManagedFamilyConfig
import io.element.android.features.ftue.api.state.FtueState
import io.element.android.features.invite.api.AcceptInvite
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.features.networkmonitor.test.FakeNetworkMonitor
import io.element.android.features.securebackup.api.SecureBackupEntryPoint
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.aRoomInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Optional
import kotlin.time.Duration.Companion.milliseconds

private val FAMILY_ROOM_ID = ManagedFamilyConfig.roomId

class FamilyRoomBootstrapperTest {
    @Test
    fun `family link waits without throwing until crypto setup is ready`() = runTest {
        val ftueState = MutableStateFlow<FtueState>(FtueState.Incomplete)
        val verifiedStatus = MutableStateFlow<SessionVerifiedStatus>(SessionVerifiedStatus.NotVerified)
        val recoveryState = MutableStateFlow(RecoveryState.DISABLED)
        val readiness = ManagedFamilyCryptoReadiness(ftueState, verifiedStatus, recoveryState)

        val result = async { readiness.awaitReady() }
        advanceUntilIdle()
        assertThat(result.isCompleted).isFalse()

        verifiedStatus.value = SessionVerifiedStatus.Verified
        recoveryState.value = RecoveryState.ENABLED
        ftueState.value = FtueState.Complete
        advanceUntilIdle()

        assertThat(result.isCompleted).isTrue()
    }

    @Test
    fun `restored room target outside the family room is rejected before resolution`() {
        val familyTarget = LoggedInFlowNode.NavTarget.Room(FAMILY_ROOM_ID.toRoomIdOrAlias())
        val otherTarget = LoggedInFlowNode.NavTarget.Room(RoomId("!otherRoom").toRoomIdOrAlias())

        assertThat(shouldRejectManagedFamilyTarget(familyTarget, isManagedCryptoReady = true)).isFalse()
        assertThat(shouldRejectManagedFamilyTarget(familyTarget, isManagedCryptoReady = false)).isTrue()
        assertThat(shouldRejectManagedFamilyTarget(otherTarget, isManagedCryptoReady = true)).isTrue()
        assertThat(shouldRejectManagedFamilyTarget(LoggedInFlowNode.NavTarget.CreateRoom, isManagedCryptoReady = true)).isTrue()
        assertThat(shouldRejectManagedFamilyTarget(LoggedInFlowNode.NavTarget.RoomDirectory, isManagedCryptoReady = true)).isTrue()
        assertThat(
            shouldRejectManagedFamilyTarget(
                LoggedInFlowNode.NavTarget.SecureBackup(SecureBackupEntryPoint.InitialTarget.ResetIdentity),
                isManagedCryptoReady = false,
            )
        ).isTrue()
        assertThat(shouldRejectManagedFamilyTarget(LoggedInFlowNode.NavTarget.Settings(), isManagedCryptoReady = false)).isFalse()
    }

    @Test
    fun `joined room opens without accepting an invitation and works after restart`() = runTest {
        var acceptCount = 0
        val client = clientWithMembership(CurrentUserMembership.JOINED)
        val bootstrapper = FamilyRoomBootstrapper(client) {
            acceptCount++
            Result.success(it)
        }

        assertThat(bootstrapper.awaitJoined(FAMILY_ROOM_ID)).isEqualTo(FAMILY_ROOM_ID)
        assertThat(bootstrapper.awaitJoined(FAMILY_ROOM_ID)).isEqualTo(FAMILY_ROOM_ID)
        assertThat(acceptCount).isEqualTo(0)
    }

    @Test
    fun `configured invitation is accepted exactly once`() = runTest {
        var acceptedRoomId: RoomId? = null
        val bootstrapper = FamilyRoomBootstrapper(clientWithMembership(CurrentUserMembership.INVITED)) {
            acceptedRoomId = it
            Result.success(it)
        }

        assertThat(bootstrapper.awaitJoined(FAMILY_ROOM_ID)).isEqualTo(FAMILY_ROOM_ID)
        assertThat(acceptedRoomId).isEqualTo(FAMILY_ROOM_ID)
    }

    @Test
    fun `left room stays on repair path then accepts a later invitation`() = runTest {
        var acceptCount = 0
        val memberships = MutableStateFlow(roomInfo(CurrentUserMembership.LEFT))
        val bootstrapper = FamilyRoomBootstrapper(clientWithMembership(memberships)) {
            acceptCount++
            Result.success(it)
        }

        val result = async { bootstrapper.awaitJoined(FAMILY_ROOM_ID) }
        advanceUntilIdle()
        assertThat(result.isCompleted).isFalse()
        assertThat(acceptCount).isEqualTo(0)

        memberships.value = roomInfo(CurrentUserMembership.INVITED)
        advanceUntilIdle()

        assertThat(result.await()).isEqualTo(FAMILY_ROOM_ID)
        assertThat(acceptCount).isEqualTo(1)
    }

    @Test
    fun `failed invitation acceptance retries while connected`() = runTest {
        var acceptCount = 0
        val bootstrapper = FamilyRoomBootstrapper(clientWithMembership(CurrentUserMembership.INVITED)) {
            acceptCount++
            if (acceptCount == 1) Result.failure(IllegalStateException("offline")) else Result.success(it)
        }

        val result = async { bootstrapper.awaitJoined(FAMILY_ROOM_ID) }
        advanceTimeBy(2.milliseconds)

        assertThat(result.await()).isEqualTo(FAMILY_ROOM_ID)
        assertThat(acceptCount).isEqualTo(2)
    }

    @Test
    fun `invitation waits for connectivity before acceptance`() = runTest {
        var acceptCount = 0
        val networkMonitor = FakeNetworkMonitor(initialStatus = NetworkStatus.Disconnected)
        val bootstrapper = FamilyRoomBootstrapper(
            client = clientWithMembership(CurrentUserMembership.INVITED),
            networkMonitor = networkMonitor,
        ) {
            acceptCount++
            Result.success(it)
        }

        val result = async { bootstrapper.awaitJoined(FAMILY_ROOM_ID) }
        advanceUntilIdle()
        assertThat(result.isCompleted).isFalse()
        assertThat(acceptCount).isEqualTo(0)

        networkMonitor.connectivity.value = NetworkStatus.Connected
        advanceUntilIdle()

        assertThat(result.await()).isEqualTo(FAMILY_ROOM_ID)
        assertThat(acceptCount).isEqualTo(1)
    }

    private fun roomInfo(membership: CurrentUserMembership) =
        Optional.of(aRoomInfo(id = FAMILY_ROOM_ID, currentUserMembership = membership))

    private fun clientWithMembership(membership: CurrentUserMembership) =
        clientWithMembership(MutableStateFlow(roomInfo(membership)))

    private fun clientWithMembership(memberships: MutableStateFlow<Optional<RoomInfo>>) = FakeMatrixClient().apply {
        getRoomInfoFlowLambda = {
            memberships
        }
    }

    private fun FamilyRoomBootstrapper(
        client: FakeMatrixClient,
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(initialStatus = NetworkStatus.Connected),
        accept: suspend (RoomId) -> Result<RoomId>,
    ) = FamilyRoomBootstrapper(
        matrixClient = client,
        acceptInvite = object : AcceptInvite {
            override suspend fun invoke(roomId: RoomId): Result<RoomId> = accept(roomId)
        },
        networkMonitor = networkMonitor,
        retryDelay = 1.milliseconds,
    )
}
