/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.auth

/** Credentials returned by a trusted service that has completed Matrix authentication. */
data class MatrixSession(
    val accessToken: String,
    val userId: String,
    val deviceId: String,
) {
    override fun toString(): String = "MatrixSession(accessToken=<redacted>, userId=$userId, deviceId=$deviceId)"
}
