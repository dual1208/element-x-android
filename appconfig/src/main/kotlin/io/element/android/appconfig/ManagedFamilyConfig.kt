/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

/** Public deployment configuration for the managed family client. */
object ManagedFamilyConfig {
    const val ENABLED = true
    const val HOMESERVER_URL = AuthenticationConfig.DEFAULT_ACCOUNT_PROVIDER_URL
    const val HOMESERVER_NAME = "8.163.2.191"
    const val ASSIGNED_ROOM_ACCOUNT_DATA_TYPE = "io.familychat.assigned_room"
    const val LANGUAGE_TAG = "zh-Hans"
}
