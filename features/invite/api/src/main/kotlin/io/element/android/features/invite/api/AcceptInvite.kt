/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.invite.api

import io.element.android.libraries.matrix.api.core.RoomId

/** Accepts an existing invitation and performs the normal invite cleanup. */
interface AcceptInvite {
    suspend operator fun invoke(roomId: RoomId): Result<RoomId>

    sealed class Failures : Exception() {
        data object InvalidInvite : Failures()
    }
}
