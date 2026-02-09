package com.example.mysms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log
import androidx.room.TypeConverter

@Database(entities = [SmsEntity::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_2_4)
                    .fallbackToDestructiveMigration() // فقط اگر از نسخه 3 و 4 destructive باشه
                    .build()

                INSTANCE = instance
                instance
            }
        }

        // Migration از نسخه 2 به 3 (فرضی)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // اگر نسخه 3 تغییراتی داشت اینجا اضافه می‌شد
                Log.d("Migration", "Migrating from 2 to 3")
            }
        }

        // Migration از نسخه 3 به 4
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d("Migration", "Migrating from 3 to 4 - Adding new columns for multipart and sync")

                // اضافه کردن ستون‌های multipart
                database.execSQL("ALTER TABLE sms_table ADD COLUMN threadId INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN messageId INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN partCount INTEGER DEFAULT 1")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN partIndex INTEGER DEFAULT 1")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN referenceNumber INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN isMultipart INTEGER DEFAULT 0") // 0 = false, 1 = true
                database.execSQL("ALTER TABLE sms_table ADD COLUMN isComplete INTEGER DEFAULT 1")  // 0 = false, 1 = true
                database.execSQL("ALTER TABLE sms_table ADD COLUMN contentType TEXT DEFAULT 'text/plain'")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN encoding TEXT DEFAULT 'UTF-8'")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN status INTEGER DEFAULT -1")

                // اضافه کردن ستون‌های sync
                database.execSQL("ALTER TABLE sms_table ADD COLUMN isSynced INTEGER DEFAULT 0")    // 0 = false, 1 = true
                database.execSQL("ALTER TABLE sms_table ADD COLUMN syncVersion INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN serverId TEXT")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN lastModified INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN isDeleted INTEGER DEFAULT 0")   // 0 = false, 1 = true

                Log.d("Migration", "Migration from 3 to 4 completed successfully")
            }
        }

        // Migration مستقیم از نسخه 2 به 4
        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d("Migration", "Migrating from 2 to 4 - Direct migration with all new columns")

                // اضافه کردن همه ستون‌های جدید یکجا
                database.execSQL("ALTER TABLE sms_table ADD COLUMN threadId INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN messageId INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN partCount INTEGER DEFAULT 1")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN partIndex INTEGER DEFAULT 1")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN referenceNumber INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN isMultipart INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN isComplete INTEGER DEFAULT 1")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN contentType TEXT DEFAULT 'text/plain'")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN encoding TEXT DEFAULT 'UTF-8'")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN status INTEGER DEFAULT -1")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN isSynced INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN syncVersion INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN serverId TEXT")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN lastModified INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE sms_table ADD COLUMN isDeleted INTEGER DEFAULT 0")

                // آپدیت lastModified برای پیام‌های موجود با date
                database.execSQL("""
                    UPDATE sms_table 
                    SET lastModified = date,
                        threadId = ABS(address_hash),
                        messageId = date/1000
                    WHERE lastModified = 0
                """)

                Log.d("Migration", "Migration from 2 to 4 completed successfully")
            }
        }
    }
}

// کلاس Converters برای Boolean و nullable fields
/*class Converters {
    @TypeConverter
    fun fromBoolean(value: Boolean): Int = if (value) 1 else 0

    @TypeConverter
    fun toBoolean(value: Int): Boolean = value == 1

    @TypeConverter
    fun fromString(value: String?): String? = value

    @TypeConverter
    fun toString(value: String?): String? = value
}*/