/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

fun Context.withManagedFamilyLocale(): Context {
    if (!ManagedFamilyConfig.ENABLED) return this
    val locale = Locale.forLanguageTag(ManagedFamilyConfig.LANGUAGE_TAG)
    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLocales(LocaleList(locale))
    }
    return createConfigurationContext(configuration)
}
