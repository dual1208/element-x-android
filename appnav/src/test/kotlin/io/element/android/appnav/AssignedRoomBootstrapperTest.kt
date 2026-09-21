/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import org.junit.Test

class AssignedRoomBootstrapperTest {
    @Test
    fun `account data parser accepts only a room id field`() {
        assertThat(parseAssignedRoomId("""{"room_id":"!assigned"}""")).isEqualTo(RoomId("!assigned"))
        assertThat(parseAssignedRoomId("""{"room_id":"#alias:example.org"}""")).isNull()
        assertThat(parseAssignedRoomId("""{"another":"!room"}""")).isNull()
        assertThat(parseAssignedRoomId("not json")).isNull()
    }

    @Test
    fun `managed navigation allows only the assigned room and settings`() {
        val assignedRoomId = RoomId("!assigned")
        val assignedTarget = LoggedInFlowNode.NavTarget.Room(assignedRoomId.toRoomIdOrAlias())
        val otherTarget = LoggedInFlowNode.NavTarget.Room(RoomId("!other").toRoomIdOrAlias())

        assertThat(shouldRejectManagedFamilyTarget(assignedTarget, assignedRoomId)).isFalse()
        assertThat(shouldRejectManagedFamilyTarget(assignedTarget, null)).isTrue()
        assertThat(shouldRejectManagedFamilyTarget(otherTarget, assignedRoomId)).isTrue()
        assertThat(shouldRejectManagedFamilyTarget(LoggedInFlowNode.NavTarget.CreateRoom, assignedRoomId)).isTrue()
        assertThat(shouldRejectManagedFamilyTarget(LoggedInFlowNode.NavTarget.Settings(), assignedRoomId)).isFalse()
    }
}
