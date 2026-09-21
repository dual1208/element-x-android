/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.notificationsettings

import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.coroutine.suspendLazy
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.notificationsettings.NotificationSettingsService
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.matrix.api.room.RoomNotificationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.matrix.rustcomponents.sdk.Action
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.NotificationSettingsDelegate
import org.matrix.rustcomponents.sdk.NotificationSettingsException
import org.matrix.rustcomponents.sdk.PushCondition
import org.matrix.rustcomponents.sdk.RuleKind
import timber.log.Timber

class RustNotificationSettingsService(
    client: Client,
    sessionCoroutineScope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : NotificationSettingsService {
    private companion object {
        const val FAMILY_MESSAGE_RULE_ID = "io.familychat.message_notifications"
        const val FAMILY_CALL_RULE_ID = "io.familychat.call-alerts.stable"
        const val FAMILY_CALL_UNSTABLE_RULE_ID = "io.familychat.call-alerts.unstable"
    }
    private val notificationSettings by suspendLazy(sessionCoroutineScope.coroutineContext + dispatchers.io) { client.getNotificationSettings() }
    private val _notificationSettingsChangeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val notificationSettingsChangeFlow: SharedFlow<Unit> = _notificationSettingsChangeFlow.asSharedFlow()

    private var notificationSettingsDelegate = object : NotificationSettingsDelegate {
        override fun settingsDidChange() {
            _notificationSettingsChangeFlow.tryEmit(Unit)
        }
    }

    suspend fun start() {
        notificationSettings.await().setDelegate(notificationSettingsDelegate)
    }

    suspend fun destroy() {
        notificationSettings.await().setDelegate(null)
    }

    override suspend fun getRoomNotificationSettings(roomId: RoomId, isEncrypted: Boolean, isOneToOne: Boolean): Result<RoomNotificationSettings> =
        runCatchingExceptions {
            notificationSettings.await().getRoomNotificationSettings(roomId.value, isEncrypted, isOneToOne).let(RoomNotificationSettingsMapper::map)
        }

    override suspend fun getDefaultRoomNotificationMode(isEncrypted: Boolean, isOneToOne: Boolean): Result<RoomNotificationMode> =
        runCatchingExceptions {
            notificationSettings.await().getDefaultRoomNotificationMode(isEncrypted, isOneToOne).let(RoomNotificationSettingsMapper::mapMode)
        }

    override suspend fun setDefaultRoomNotificationMode(
        isEncrypted: Boolean,
        mode: RoomNotificationMode,
        isDM: Boolean
    ): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            try {
                notificationSettings.await().setDefaultRoomNotificationMode(isEncrypted, isDM, mode.let(RoomNotificationSettingsMapper::mapMode))
            } catch (exception: NotificationSettingsException.RuleNotFound) {
                // `setDefaultRoomNotificationMode` updates multiple rules including unstable rules (e.g. the polls push rules defined in the MSC3930)
                // since production home servers may not have these rules yet, we drop the RuleNotFound error
                Timber.w("Unable to find the rule: ${exception.ruleId}")
            }
        }
    }

    override suspend fun setRoomNotificationMode(roomId: RoomId, mode: RoomNotificationMode): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().setRoomNotificationMode(roomId.value, mode.let(RoomNotificationSettingsMapper::mapMode))
        }
    }

    override suspend fun restoreDefaultRoomNotificationMode(roomId: RoomId): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().restoreDefaultRoomNotificationMode(roomId.value)
        }
    }

    override suspend fun muteRoom(roomId: RoomId): Result<Unit> = setRoomNotificationMode(roomId, RoomNotificationMode.MUTE)

    override suspend fun unmuteRoom(roomId: RoomId, isEncrypted: Boolean, isOneToOne: Boolean) = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().unmuteRoom(roomId.value, isEncrypted, isOneToOne)
        }
    }

    override suspend fun isRoomMentionEnabled(): Result<Boolean> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().isRoomMentionEnabled()
        }
    }

    override suspend fun setRoomMentionEnabled(enabled: Boolean): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().setRoomMentionEnabled(enabled)
        }
    }

    override suspend fun isCallEnabled(): Result<Boolean> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().isCallEnabled()
        }
    }

    override suspend fun isCallEnabled(roomId: RoomId): Result<Boolean> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val rules = customOverrideRules()
            rules.findEnabled(FAMILY_CALL_RULE_ID)
                ?: rules.findEnabled(FAMILY_CALL_UNSTABLE_RULE_ID)
                ?: notificationSettings.await().isCallEnabled()
        }
    }

    override suspend fun setCallEnabled(enabled: Boolean): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().setCallEnabled(enabled)
        }
    }

    override suspend fun setCallEnabled(roomId: RoomId, enabled: Boolean): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().setCallEnabled(enabled)
            setTypedOverrideRule(FAMILY_CALL_RULE_ID, roomId, "m.rtc.notification", enabled)
            setTypedOverrideRule(FAMILY_CALL_UNSTABLE_RULE_ID, roomId, "org.matrix.msc4075.rtc.notification", enabled)
            _notificationSettingsChangeFlow.tryEmit(Unit)
            Unit
        }
    }

    override suspend fun isMessageEnabled(roomId: RoomId): Result<Boolean> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val rule = customOverrideRules()
                .firstOrNull {
                    it.jsonObject["rule_id"]?.jsonPrimitive?.content == FAMILY_MESSAGE_RULE_ID
                }
                ?.jsonObject
            if (rule == null) {
                notificationSettings.await()
                    .getRoomNotificationSettings(roomId.value, false, false)
                    .mode != org.matrix.rustcomponents.sdk.RoomNotificationMode.MUTE
            } else {
                rule["enabled"]?.jsonPrimitive?.booleanOrNull != false &&
                    rule["actions"]?.jsonArray?.any { (it as? JsonPrimitive)?.contentOrNull == "notify" } == true
            }
        }
    }

    override suspend fun setMessageEnabled(roomId: RoomId, enabled: Boolean): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().setCustomPushRule(
                FAMILY_MESSAGE_RULE_ID,
                RuleKind.Override,
                if (enabled) listOf(Action.Notify) else emptyList(),
                listOf(
                    PushCondition.EventMatch("room_id", roomId.value),
                    PushCondition.EventMatch("type", "m.room.message"),
                ),
            )
            _notificationSettingsChangeFlow.tryEmit(Unit)
            Unit
        }
    }

    private suspend fun customOverrideRules() = notificationSettings.await().getRawPushRules()
        ?.let(Json::parseToJsonElement)
        ?.jsonObject
        ?.get("global")
        ?.jsonObject
        ?.get("override")
        ?.jsonArray
        .orEmpty()

    private fun List<kotlinx.serialization.json.JsonElement>.findEnabled(ruleId: String): Boolean? =
        firstOrNull { it.jsonObject["rule_id"]?.jsonPrimitive?.content == ruleId }
            ?.jsonObject
            ?.let { rule ->
                rule["enabled"]?.jsonPrimitive?.booleanOrNull != false &&
                    rule["actions"]?.jsonArray?.any { (it as? JsonPrimitive)?.contentOrNull == "notify" } == true
            }

    private suspend fun setTypedOverrideRule(ruleId: String, roomId: RoomId, eventType: String, enabled: Boolean) {
        notificationSettings.await().setCustomPushRule(
            ruleId,
            RuleKind.Override,
            if (enabled) listOf(Action.Notify) else emptyList(),
            listOf(
                PushCondition.EventMatch("room_id", roomId.value),
                PushCondition.EventMatch("type", eventType),
            ),
        )
    }

    override suspend fun isInviteForMeEnabled(): Result<Boolean> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().isInviteForMeEnabled()
        }
    }

    override suspend fun setInviteForMeEnabled(enabled: Boolean): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            notificationSettings.await().setInviteForMeEnabled(enabled)
        }
    }

    override suspend fun getRoomsWithUserDefinedRules(): Result<List<RoomId>> =
        runCatchingExceptions {
            notificationSettings.await().getRoomsWithUserDefinedRules(enabled = true).map(::RoomId)
        }

    override suspend fun canHomeServerPushEncryptedEventsToDevice(): Result<Boolean> =
        runCatchingExceptions {
            notificationSettings.await().canPushEncryptedEventToDevice()
        }

    override suspend fun getRawPushRules(): Result<String?> = runCatchingExceptions {
        notificationSettings.await().getRawPushRules()
    }
}
