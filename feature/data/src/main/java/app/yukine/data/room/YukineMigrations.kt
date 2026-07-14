package app.yukine.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object YukineMigrations {
    const val TARGET_VERSION: Int = 15

    val all: Array<Migration> = (1 until TARGET_VERSION).map { startVersion ->
        object : Migration(startVersion, TARGET_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                YukineSchema.normalizeV15(db)
            }
        }
    }.toTypedArray()
}
