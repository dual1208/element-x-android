/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import io.element.android.appconfig.ManagedFamilyConfig
import io.element.android.features.ftue.api.state.FtueState
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

internal class ManagedFamilyCryptoReadiness(
    private val ftueState: StateFlow<FtueState>,
    private val sessionVerifiedStatus: StateFlow<SessionVerifiedStatus>,
    private val recoveryState: StateFlow<RecoveryState>,
) {
    fun isReady(): Boolean = isReady(
        ftueState = ftueState.value,
        sessionVerifiedStatus = sessionVerifiedStatus.value,
        recoveryState = recoveryState.value,
    )

    suspend fun awaitReady() {
        if (!ManagedFamilyConfig.ENABLED) return
        combine(ftueState, sessionVerifiedStatus, recoveryState) { ftue, verification, recovery ->
            isReady(ftue, verification, recovery)
        }.first { it }
    }

    private fun isReady(
        ftueState: FtueState,
        sessionVerifiedStatus: SessionVerifiedStatus,
        recoveryState: RecoveryState,
    ): Boolean = !ManagedFamilyConfig.ENABLED ||
        ftueState is FtueState.Complete &&
        sessionVerifiedStatus == SessionVerifiedStatus.Verified &&
        recoveryState == RecoveryState.ENABLED
}
