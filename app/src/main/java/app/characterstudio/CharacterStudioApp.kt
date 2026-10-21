package app.characterstudio

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.characterstudio.data.AppDatabase
import app.characterstudio.data.ProjectRepository

class AppContainer(context: Context) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "character_studio.db").build()
    val projects = ProjectRepository(db.projectDao())
}

class CharacterStudioApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
