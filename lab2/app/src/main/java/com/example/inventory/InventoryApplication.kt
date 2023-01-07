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

import android.app.Application
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.example.inventory.data.ItemRoomDatabase
import java.security.KeyStore
import javax.crypto.KeyGenerator

@RequiresApi(Build.VERSION_CODES.R)
class InventoryApplication : Application() {
    /* Рандомный ключ, для шифрования данных */
    val alias = "poiuy"

    val database: ItemRoomDatabase by lazy {
        /* Обращаемся к KeyStore */
        val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        /* Обращаемся к сущности, где есть секретный ключ */
        val secretKeyEntry = ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry

        /* Если нет ключа, то... */
        val secretKey = if (secretKeyEntry == null) {
            /* Создаём генератор ключа */
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(
                KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    .build()
            )
            /* И генерируем ключ */
            keyGenerator.generateKey()
        } else {
            /*Если ключ есть ,то просто берём его */
            secretKeyEntry.secretKey
        }

        /* Обращаемся к БД и передём ключ шифрования */
        ItemRoomDatabase.getDatabase(this, secretKey.toString())

        /* Метод для создания незашифрованной БД (для 2 и 3 лабы) */
//        ItemRoomDatabase.getDatabase(this)
    }
}
