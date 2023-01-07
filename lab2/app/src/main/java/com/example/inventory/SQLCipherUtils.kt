/*
 * Copyright (c) 2012-2021 CommonsWare, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.inventory

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/* Вспомогательный класс (объект) */
object SQLCipherUtils {
  /**
   * The detected state of the database, based on whether we can open it
   * without a passphrase.
   */
  enum class State {
    DOES_NOT_EXIST, UNENCRYPTED, ENCRYPTED
  }

  fun getDatabaseState(ctxt: Context, dbPath: File): State {
    SQLiteDatabase.loadLibs(ctxt)

    if (dbPath.exists()) {
      var db: SQLiteDatabase? = null

      return try {
        db = SQLiteDatabase.openDatabase(
          dbPath.absolutePath,
          "",
          null,
          SQLiteDatabase.OPEN_READONLY
        )
        db.version
        State.UNENCRYPTED
      } catch (e: Exception) {
        State.ENCRYPTED
      } finally {
        db?.close()
      }
    }

    return State.DOES_NOT_EXIST
  }

  fun encryptTo(
    ctxt: Context,
    originalFile: File,
    targetFile: File,
    passphrase: ByteArray?
  ) {
    SQLiteDatabase.loadLibs(ctxt)

    if (originalFile.exists()) {
      val originalDb = SQLiteDatabase.openDatabase(
        originalFile.absolutePath,
        "",
        null,
        SQLiteDatabase.OPEN_READWRITE
      )
      val version = originalDb.version

      originalDb.close()

      val db = SQLiteDatabase.openOrCreateDatabase(
        targetFile.absolutePath,
        passphrase,
        null
      )

      //language=text
      val st = db.compileStatement("ATTACH DATABASE ? AS plaintext KEY ''")

      st.bindString(1, originalFile.absolutePath)
      st.execute()
      db.rawExecSQL("SELECT sqlcipher_export('main', 'plaintext')")
      db.rawExecSQL("DETACH DATABASE plaintext")
      db.version = version
      st.close()
      db.close()
    } else {
      throw FileNotFoundException(originalFile.absolutePath + " not found")
    }
  }

  @Throws(IOException::class)
  fun decryptTo(
    ctxt: Context,
    originalFile: File,
    targetFile: File,
    passphrase: ByteArray?
  ) {
    SQLiteDatabase.loadLibs(ctxt)

    if (originalFile.exists()) {
      val originalDb = SQLiteDatabase.openDatabase(
        originalFile.absolutePath,
        passphrase,
        null,
        SQLiteDatabase.OPEN_READWRITE,
        null,
        null
      )

      SQLiteDatabase.openOrCreateDatabase(
        targetFile.absolutePath,
        "",
        null
      ).close() // create an empty database

      //language=text
      val st =
        originalDb.compileStatement("ATTACH DATABASE ? AS plaintext KEY ''")

      st.bindString(1, targetFile.absolutePath)
      st.execute()
      originalDb.rawExecSQL("SELECT sqlcipher_export('plaintext')")
      originalDb.rawExecSQL("DETACH DATABASE plaintext")

      val version = originalDb.version

      st.close()
      originalDb.close()

      val db = SQLiteDatabase.openOrCreateDatabase(
        targetFile.absolutePath,
        "",
        null
      )

      db.version = version
      db.close()
    } else {
      throw FileNotFoundException(originalFile.absolutePath + " not found")
    }
  }
}