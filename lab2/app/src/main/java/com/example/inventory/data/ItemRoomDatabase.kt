package com.example.inventory.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.inventory.SQLCipherUtils
import net.sqlcipher.database.SupportFactory
import java.io.IOException

private const val DB_Name = "item_database"


/* **ВАЖНО** Если менять поля в БД, то надо менять версию по увеличению */
@Database(entities = [Item::class], version = 3, exportSchema = false)
abstract class ItemRoomDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    companion object {
        @Volatile
        private var INSTANCE: ItemRoomDatabase? = null

        /* Этот код сразу был, для создания БД */
        fun getDatabase(context: Context): ItemRoomDatabase {
            return INSTANCE?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ItemRoomDatabase::class.java,
                    "item_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                return instance
            }
        }

        /* Весь пример из презентации */
        fun getDatabase(context: Context, password: String): ItemRoomDatabase {
            return INSTANCE ?: synchronized(this) {

                /* Обращаемся к БД и определяем состояние БД (зашифрованна или нет) */
                val dbFile = context.getDatabasePath(DB_Name)
                val password = password.toByteArray()
                val state = SQLCipherUtils.getDatabaseState(context, dbFile)

                /*Если нет, то через третью БД зашифровываем */
                if (state == SQLCipherUtils.State.UNENCRYPTED) {
                    val dbTemp = context.getDatabasePath("_temp.db")

                    dbTemp.delete()

                    SQLCipherUtils.encryptTo(context, dbFile, dbTemp, password)

                    val dbBackup = context.getDatabasePath("_backup.db")

                    if (dbFile.renameTo(dbBackup)) {
                        if (dbTemp.renameTo(dbFile)) {
                            dbBackup.delete()
                        } else {
                            dbBackup.renameTo(dbFile)
                            throw IOException("Could not rename $dbTemp to $dbFile")
                        }
                    } else {
                        dbTemp.delete()
                        throw IOException("Could not rename $dbFile to $dbBackup")
                    }
                }
                /* И расшифровываем БД с помощью пароля */
                val supportFactory = SupportFactory(password)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ItemRoomDatabase::class.java,
                    DB_Name,
                )
                    .openHelperFactory(supportFactory)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}