package app.characterstudio

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.characterstudio.auth.AccountRepository
import app.characterstudio.auth.AuthRepository
import app.characterstudio.auth.SecureSessionStore
import app.characterstudio.auth.SupabaseAccountApi
import app.characterstudio.auth.SupabaseAuthApi
import app.characterstudio.assets.AssetDatabase
import app.characterstudio.assets.AssetFiles
import app.characterstudio.assets.AssetImageStore
import app.characterstudio.assets.SupabaseAssetRemote
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
    /** هل ضُبط الخادم وقت البناء؟ (SUPABASE_URL وSUPABASE_ANON_KEY وCLOUDINARY_CLOUD_NAME) */
    val libraryConfigured: Boolean =
        BuildConfig.SUPABASE_URL.isNotEmpty() && BuildConfig.SUPABASE_ANON_KEY.isNotEmpty() && BuildConfig.CLOUDINARY_CLOUD_NAME.isNotEmpty()
    val cloudName: String = BuildConfig.CLOUDINARY_CLOUD_NAME
    /** أسماء المتغيرات التي لم تُحقن في هذا البناء (الأسماء فقط، بلا قيم) لتشخيص إعداد CI. */
    val missingConfig: List<String> = buildList {
        if (BuildConfig.SUPABASE_URL.isEmpty()) add("SUPABASE_URL")
        if (BuildConfig.SUPABASE_ANON_KEY.isEmpty()) add("SUPABASE_ANON_KEY")
        if (BuildConfig.CLOUDINARY_CLOUD_NAME.isEmpty()) add("CLOUDINARY_CLOUD_NAME")
    }

    /** الحسابات تحتاج SUPABASE_URL وSUPABASE_ANON_KEY فقط. */
    val supabaseConfigured: Boolean = BuildConfig.SUPABASE_URL.isNotEmpty() && BuildConfig.SUPABASE_ANON_KEY.isNotEmpty()
    val authRepo: AuthRepository = AuthRepository(
        api = if (supabaseConfigured) SupabaseAuthApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) else null,
        store = SecureSessionStore(context.applicationContext),
    )
    val account: AccountRepository = AccountRepository(
        authRepo,
        if (supabaseConfigured) SupabaseAccountApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) else null,
    )

    val assetFiles = AssetFiles(context.applicationContext, BuildConfig.CLOUDINARY_CLOUD_NAME)
    val imageStore = AssetImageStore(assetFiles)
    val assets: AssetRepository by lazy {
        AssetRepository(
            assetDb.assetDao(),
            remote = if (libraryConfigured) SupabaseAssetRemote(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) { authRepo.accessToken() } else null,
            appVersion = BuildConfig.VERSION_CODE,
        )
    }
}

class CharacterStudioApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        DailyReminder.createChannel(this)
        DailyReminder.schedule(this, container.settings.state.value, ExistingWorkPolicy.KEEP)
    }
}
