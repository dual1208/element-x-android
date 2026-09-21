/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x

import android.app.Application
import android.content.Context
import androidx.compose.material3.ComposeMaterial3Flags.isAnchoredDraggableComponentsStrictOffsetCheckEnabled
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.startup.AppInitializer
import androidx.work.Configuration
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.di.DependencyInjectionGraphOwner
import io.element.android.libraries.matrix.api.SdkMetadata
import io.element.android.libraries.workmanager.api.di.MetroWorkerFactory
import io.element.android.appconfig.ManagedFamilyConfig
import io.element.android.appconfig.withManagedFamilyLocale
import io.element.android.x.di.AppGraph
import io.element.android.x.di.ApplicationBindings
import io.element.android.x.info.logApplicationInfo
import io.element.android.x.initializer.CacheCleanerInitializer
import io.element.android.x.initializer.CrashInitializer
import io.element.android.x.initializer.PlatformInitializer

class ElementXApplication : Application(), DependencyInjectionGraphOwner, Configuration.Provider {
    override val graph: AppGraph = createGraphFactory<AppGraph.Factory>().create(this)

    override val workManagerConfiguration: Configuration = Configuration.Builder()
        .setWorkerFactory(MetroWorkerFactory(graph.workerProviders))
        .build()

    @Inject lateinit var sdkMetadata: SdkMetadata

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withManagedFamilyLocale())
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate() {
        super.onCreate()
        AppInitializer.getInstance(this).apply {
            if (!ManagedFamilyConfig.ENABLED) {
                initializeComponent(CrashInitializer::class.java)
            }
            initializeComponent(PlatformInitializer::class.java)
            initializeComponent(CacheCleanerInitializer::class.java)
        }

        bindings<ApplicationBindings>().inject(this)
        logApplicationInfo(this, sdkMetadata.sdkGitSha)

        // Disable the strict offset check for anchored draggable components, as it can cause issues with bottom sheets.
        // Remove once https://issuetracker.google.com/issues/477038695 is fixed.
        isAnchoredDraggableComponentsStrictOffsetCheckEnabled = false
    }
}
