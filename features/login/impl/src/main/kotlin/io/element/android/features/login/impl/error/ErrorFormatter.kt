/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.error

import androidx.annotation.StringRes
import io.element.android.appconfig.ManagedFamilyConfig
import io.element.android.features.login.impl.R
import io.element.android.features.login.impl.managed.ManagedFamilyLoginException
import io.element.android.libraries.matrix.api.auth.AuthErrorCode
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.errorCode
import io.element.android.libraries.ui.strings.CommonStrings

@StringRes
fun loginError(
    throwable: Throwable
): Int {
    if (throwable is ManagedFamilyLoginException) {
        return when (throwable) {
            ManagedFamilyLoginException.InvalidCredentials -> R.string.screen_login_error_invalid_credentials
            ManagedFamilyLoginException.ConnectionFailed,
            ManagedFamilyLoginException.TemporarilyUnavailable,
            ManagedFamilyLoginException.InvalidResponse -> R.string.family_login_connection_failed
            ManagedFamilyLoginException.MissingClientCertificate,
            ManagedFamilyLoginException.ReleaseNotAllowed -> R.string.family_login_update_required
        }
    }
    val authException = throwable as? AuthenticationException
        ?: return if (ManagedFamilyConfig.ENABLED) R.string.family_login_connection_failed else CommonStrings.error_unknown
    return when (authException.errorCode) {
        AuthErrorCode.FORBIDDEN -> R.string.screen_login_error_invalid_credentials
        AuthErrorCode.USER_DEACTIVATED -> R.string.screen_login_error_deactivated_account
        AuthErrorCode.UNKNOWN -> if (ManagedFamilyConfig.ENABLED) {
            R.string.family_login_connection_failed
        } else {
            CommonStrings.error_unknown
        }
    }
}
