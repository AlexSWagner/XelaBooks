package com.xelabooks.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xelabooks.app.model.AudioBook
import com.xelabooks.app.model.Chapter

// Migration from version 2 to 3 to add currentChapterId column
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add the currentChapterId column to the audiobooks table with a null default value
        database.execSQL("ALTER TABLE audiobooks ADD COLUMN currentChapterId TEXT DEFAULT NULL")
    }
}

@Database(entities = [AudioBook::class, Chapter::class], version = 3, exportSchema = false)
abstract class AudioBookDatabase : RoomDatabase() {
    
    abstract fun audioBookDao(): AudioBookDao
    abstract fun chapterDao(): ChapterDao
    
    companion object {
        @Volatile
        private var INSTANCE: AudioBookDatabase? = null
        
        fun getDatabase(context: Context): AudioBookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AudioBookDatabase::class.java,
                    "audiobook_database"
                )
                .addMigrations(MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 