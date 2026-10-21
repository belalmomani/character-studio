package app.characterstudio

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.characterstudio.assets.AssetDatabase
import app.characterstudio.assets.AssetRepository
import app.characterstudio.data.AppDatabase
import app.characterstudio.data.ProjectRepository
import app.characterstudio.settings.DailyReminder
import app.characterstudio.settings.SettingsStore
import androidx.work.ExistingWorkPolicy

class AppContainer(context: Context) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "character_studio.db").build()
    val projects = ProjectRepository(db.projectDao())
    val settings = SettingsStore(context.applicationContext)

    // ذاكرة مؤقتة قابلة لإعادة المزامنة، لذلك يجوز مسحها عند تغيّر المخطط (بخلاف قاعدة المشاريع).
    private val assetDb by lazy {
        Room.databaseBuilder(context.applicationContext, AssetDatabase::class.java, "assets_cache.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    val assets: AssetRepository by lazy { AssetRepository(assetDb.assetDao()) }
}

class CharacterStudioApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        DailyReminder.createChannel(this)
        DailyReminder.schedule(this, container.settings.state.value, ExistingWorkPolicy.KEEP)
    }
}
