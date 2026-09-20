/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias

/** Public deployment configuration for the managed family client. */
object ManagedFamilyConfig {
    const val ENABLED = true
    const val HOMESERVER_URL = AuthenticationConfig.DEFAULT_ACCOUNT_PROVIDER_URL
    const val HOMESERVER_NAME = "8.163.2.191"
    const val ROOM_ID = "!zeH0LfJ1UQUIIR1Zm0or2b843_84zsDIYH4qxw-kDew"

    val roomId = RoomId(ROOM_ID)

    fun isAllowedRoom(roomId: RoomId): Boolean = !ENABLED || roomId == this.roomId

    fun isAllowedRoom(roomIdOrAlias: RoomIdOrAlias): Boolean = !ENABLED ||
        roomIdOrAlias is RoomIdOrAlias.Id && isAllowedRoom(roomIdOrAlias.roomId)
}
