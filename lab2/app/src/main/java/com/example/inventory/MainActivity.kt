/*
 * Copyright (C) 2021 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.inventory

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.example.inventory.data.Item
import com.google.gson.Gson
import java.io.*


class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private lateinit var navController: NavController

    /* Переменная БД */
    private val viewModel by viewModels<InventoryViewModel> {
        InventoryViewModelFactory(
            (application as InventoryApplication).database.itemDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* Настраиваем навигатор */
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        setupActionBarWithNavController(this, navController)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        /* Добавляем своё меню (чтоб можно было добавлять защифрованные файлы) */
        val inflater = menuInflater
        inflater.inflate(R.menu.file_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            /* Если выбираем пункт, то запускаем активити (галерею) */
            R.id.load_from_file -> {
                openFile(Uri.parse("/"))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openFile(pickerInitialUri: Uri) {
        /* Всякие настройки для запуска активити */
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }
        /* Сам запуск активити (галереи) */
        requestUri.launch(intent)
    }

    /* Метод нужен для получания названия файла (взят из оф. документации) */
    @SuppressLint("Range")
    private fun dumpFileName(uri: Uri) : String{
        val cursor: Cursor? = contentResolver.query(
            uri, null, null, null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val displayName: String =
                    it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                return displayName
            }
        }
        return ""
    }

    /* Обработчик активити (галереи) (когда выбрали или не выбрали фотографию) */
    private var requestUri = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        /* Если всё хорошо и пользователь выбрал фотографию */
        if (result != null && result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                intent.data?.let { fileUri ->

                    /* Создаём ключ шифрования (из оф. документации) */
                    val mainKey = MasterKey.Builder(applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    /* Создаём временный файл (без него не получается расшифровать) */
                    val cacheFileToWrite  = File(applicationContext.cacheDir, dumpFileName(fileUri))

                    /* Копируем данные из выбранного файла во временный */
                    applicationContext.contentResolver.openInputStream(fileUri)!!.copyTo(
                        applicationContext.contentResolver.openOutputStream(cacheFileToWrite.toUri())!!
                    )

                    /* Расшифровываем данные во временном файле (из оф. документации) */
                    val encryptedFile = EncryptedFile.Builder(
                        applicationContext,
                        cacheFileToWrite,
                        mainKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()

                    try {
                        /* Считываем данные из файла в байтах */
                        val inputStream = encryptedFile.openFileInput()
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        var nextByte: Int = inputStream.read()
                        while (nextByte != -1) {
                            byteArrayOutputStream.write(nextByte)
                            nextByte = inputStream.read()
                        }

                        /* Надо для нужного типа данных */
                        val plaintext = byteArrayOutputStream.toByteArray()

                        /* С помощью библеотеки Gson байты(текст) превращаем в нужный класс */
                        val gson = Gson()
                        val item = gson.fromJson(plaintext.toString(Charsets.UTF_8), Item::class.java)

                        /* Добавляем в нашу БД и сообщаем, что всё хорошо */
                        viewModel.addNewItem(item)
                        Toast.makeText(this, "Файл успешно добавлен", Toast.LENGTH_SHORT).show()
                    }
                    catch (e: IOException) {
                        /* Если в ходе чтения возникла ошибка, то выводим ошибку в стек */
                        /* Чаще всего просто не смог расшифровать файл, потому что ключ не подходит */
                        e.printStackTrace()
                        Toast.makeText(this, "Невозможно открыть файл!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Handle navigation when the user chooses Up from the action bar.
     */
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
