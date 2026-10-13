package com.brandon.videodownloader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Download::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "library.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * Una migración es como un script de EF Core `Add-Migration`: cambia el esquema SIN perder
         * los datos que ya tienes. v2 agrega la columna `vault`; lo que ya estaba guardado en
         * modo privado (rutas file://) pasa directo a la Bóveda.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN vault INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE downloads SET vault = 1 WHERE fileUri LIKE 'file:%'")
            }
        }
    }
}
