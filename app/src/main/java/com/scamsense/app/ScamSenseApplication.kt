package com.scamsense.app

import android.app.Application
import android.content.Context
import com.scamsense.app.data.AssistantClient
import com.scamsense.app.data.BlobStore
import com.scamsense.app.data.HistoryRepository
import com.scamsense.app.data.InMemoryBlobStore
import com.scamsense.app.data.SecureFileStore
import com.scamsense.app.data.SettingsRepository
import com.scamsense.core.engine.ScamAnalyzer

class ScamSenseApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Manual dependency injection: the object graph is tiny, so a DI framework would only
 * add startup time and APK size.
 */
class AppContainer(context: Context) {
    val analyzer: ScamAnalyzer by lazy { ScamAnalyzer() }
    val settingsRepository = SettingsRepository(secureStore(context, "settings.bin"))
    val historyRepository = HistoryRepository(secureStore(context, "history.bin"))
    val assistantClient = AssistantClient()

    private fun secureStore(context: Context, name: String): BlobStore =
        runCatching { SecureFileStore(context.applicationContext, name) }
            .getOrElse { InMemoryBlobStore() }
}
