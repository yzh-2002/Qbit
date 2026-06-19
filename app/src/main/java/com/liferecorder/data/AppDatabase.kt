package com.liferecorder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [LifeRecord::class, QuietRule::class, HolidayCache::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lifeRecordDao(): LifeRecordDao
    abstract fun quietRuleDao(): QuietRuleDao
    abstract fun holidayCacheDao(): HolidayCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 -> v2: 新增 quiet_rules 表 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS quiet_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        startHour INTEGER NOT NULL,
                        startMinute INTEGER NOT NULL,
                        endHour INTEGER NOT NULL,
                        endMinute INTEGER NOT NULL,
                        applyMode INTEGER NOT NULL,
                        enabled INTEGER NOT NULL
                    )
                """)
            }
        }

        /** v2 -> v3: 新增 holiday_cache 表 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS holiday_cache (
                        date TEXT PRIMARY KEY NOT NULL,
                        type INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        country TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_holiday_country_year ON holiday_cache(country, year)")
            }
        }

        /** v3 -> v4: life_records 表新增 startTime 字段 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE life_records ADD COLUMN startTime INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "life_recorder_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // 首次安装时插入默认免打扰规则
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.quietRuleDao()?.let { dao ->
                                    dao.insert(QuietRule(
                                        name = "睡觉时间",
                                        startHour = 1, startMinute = 0,
                                        endHour = 9, endMinute = 0,
                                        applyMode = 0, enabled = true
                                    ))
                                    dao.insert(QuietRule(
                                        name = "工作时间",
                                        startHour = 9, startMinute = 30,
                                        endHour = 20, endMinute = 0,
                                        applyMode = 1, enabled = true
                                    ))
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
