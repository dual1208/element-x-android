/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object LearnMoreConfig {
    private const val MANAGED_SUPPORT_URL = "https://8.163.2.191/family/support/"

    val ENCRYPTION_URL: String = managedOrUpstream("https://element.io/help#encryption")
    val DEVICE_VERIFICATION_URL: String = managedOrUpstream("https://element.io/help#encryption-device-verification")
    val SECURE_BACKUP_URL: String = managedOrUpstream("https://element.io/help#encryption5")
    val IDENTITY_CHANGE_URL: String = managedOrUpstream("https://element.io/help#encryption18")
    val HISTORY_VISIBLE_URL: String = managedOrUpstream("https://element.io/en/help#e2ee-history-sharing")

    private fun managedOrUpstream(upstream: String): String =
        if (ManagedFamilyConfig.ENABLED) MANAGED_SUPPORT_URL else upstream
}
