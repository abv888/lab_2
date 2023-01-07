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


import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.inventory.data.Item
import com.example.inventory.data.getFormattedPrice
import com.example.inventory.databinding.FragmentItemDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import java.io.*
import java.nio.charset.StandardCharsets

/**
 * [ItemDetailFragment] displays the details of the selected item.
 */
class ItemDetailFragment : Fragment() {
    private val navigationArgs: ItemDetailFragmentArgs by navArgs()

    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

    private val PREFS_FILE = "Setting"

    lateinit var item: Item
    /* Переменная БД */
    private val viewModel: InventoryViewModel by activityViewModels {
        InventoryViewModelFactory(
            (activity?.application as InventoryApplication).database.itemDao()
        )
    }

    private fun bind(item: Item) {
        /* Зашифровывает/расшифровывает настройки приложения (из оф. документации) */
        /* Что-то типа реестра на ПК, только для android */
        val sharedPreferences = EncryptedSharedPreferences.create(
            requireContext(),
            PREFS_FILE,
            MasterKey.Builder(requireContext()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        binding.apply {
            /* Заполняем данные */
            itemName.text = item.itemName
            itemPrice.text = item.getFormattedPrice()
            itemCount.text = item.quantityInStock.toString()
            itemProviderName.text = item.providerName
            itemProviderEmail.text = item.providerEmail
            itemProviderPhone.text = item.providerPhone
            itemRecord.text = item.record.toString()

            /* Проверяем поле в "реестре" CheckBoxHide, если false, то показываем данные */
            if (!sharedPreferences.getBoolean("CheckBoxHide",false)){
                itemProviderName.transformationMethod = HideReturnsTransformationMethod.getInstance();
                itemProviderEmail.transformationMethod = HideReturnsTransformationMethod.getInstance();
                itemProviderPhone.transformationMethod = HideReturnsTransformationMethod.getInstance();
            }
            else {
                /* Иначе скрываем */
                itemProviderName.transformationMethod = PasswordTransformationMethod.getInstance();
                itemProviderEmail.transformationMethod = PasswordTransformationMethod.getInstance();
                itemProviderPhone.transformationMethod = PasswordTransformationMethod.getInstance();
            }

            /* Если количество товаров = 0, то продать нельзя (кнопка не акстивная) */
            sellItem.isEnabled = viewModel.isStockAvailable(item)

            /* При нажатии на кнопку уменьшается количество товара на 1*/
            sellItem.setOnClickListener { viewModel.sellItem(item) }

            /* При нажатии удаляется товар */
            deleteItem.setOnClickListener { showConfirmationDialog() }

            /* При нажатии переходит во фрагмент с редактированием товара */
            editItem.setOnClickListener { editItem() }
            /* Если поле в "реестре" true, то на кнопку нельзя нажать */
            shareItem.isEnabled = !sharedPreferences.getBoolean("CheckBoxForbid", false)

            /* При нажатии открывается окно для "поделиться" в приложениях */
            shareItem.setOnClickListener { share(item) }

            /* При нажатии сохраняет зашифрованные файл об этом товаре */
            saveInFileBtn.setOnClickListener {
                createFile(Uri.parse(requireContext().filesDir.toString()))
            }

        }
    }

    private fun createFile(pickerInitialUri: Uri) {
        /* Настраиваем активити для сохранения зашифрованного файла об товаре */
        /* Там можно менять название файла, но его лучше не менять, иначе может не сработать */
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, item.itemName)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }
        /* Запускаем активити */
        requestUri.launch(intent)
    }

    private var requestUri = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val contentResolver = requireContext().contentResolver

        /* Если выбрали товар , то ...*/
        if (result != null && result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                intent.data?.let { fileUri ->
                    /* Создаём ключ для шифрования */
                    val mainKey = MasterKey.Builder(requireContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                    /* Создаём временный файл для шифрования */
                    val cacheFileToWrite  = File(requireContext().cacheDir, item.itemName + ".json")

                    /* Создаём файл для шифрования (в нём все данные шифруются) */
                    val encryptedFile = EncryptedFile.Builder(
                        requireContext(),
                        cacheFileToWrite,
                        mainKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()

                    /* Проверяем, если есть файл, то удаляем */
                    if (cacheFileToWrite.exists()) {
                        cacheFileToWrite.delete()
                    }

                    try {
                        /* С помощью библеотеки Gson наш класс превращаем в json, а потом в массив байтов */
                        val gson = Gson()
                        val fileContent = gson.toJson(item)
                            .toByteArray(StandardCharsets.UTF_8)

                        /* Записываем массив байтов в наш зашифрованныей файл */
                        val encryptedOutputStream = encryptedFile.openFileOutput()
                        encryptedOutputStream.apply {
                            write(fileContent)
                            flush()
                            close()
                        }
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    try {
                        /* А теперь мы все зашифрованные данные записываем в файл, который выбрал пользователь */
                        contentResolver.openFileDescriptor(fileUri, "w")?.use {
                            if (!cacheFileToWrite.exists()) {
                                throw NoSuchFileException(cacheFileToWrite )
                            }

                            FileOutputStream(it.fileDescriptor).use {
                                it.write(
                                    cacheFileToWrite.inputStream().readBytes()
                                )
                            }
                        }
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    }
            }
        }
    }

    private fun share(item: Item) {
        /* Настраиваем активити, для кнопки поделиться */
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        sharingIntent.putExtra(Intent.EXTRA_TEXT, item.toString())

        /* Запускаем активити */
        startActivity(Intent.createChooser(sharingIntent, null))
    }

    private fun editItem() {
        /*По нажатию на кнопку переходим в окно редактирования товара и передаём нужный id товара */
        val action = ItemDetailFragmentDirections.actionItemDetailFragmentToAddItemFragment(
            getString(R.string.edit_fragment_title),
            item.id
        )
        this.findNavController().navigate(action)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        /* Когда создалось view обрабатываем переданные данные и передаём в функцию, я отображения данных */
        val id = navigationArgs.itemId
        viewModel.retrieveItem(id).observe(this.viewLifecycleOwner) { selectedItem ->
            item = selectedItem
            bind(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Displays an alert dialog to get the user's confirmation before deleting the item.
     */
    private fun showConfirmationDialog() {
        /* При нажатии на кнопку удалить, высплывает модальное окно и если "да", то удаляем товар */
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(android.R.string.dialog_alert_title))
            .setMessage(getString(R.string.delete_question))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.no)) { _, _ -> }
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                deleteItem()
            }
            .show()
    }

    /**
     * Deletes the current item and navigates to the list fragment.
     */
    private fun deleteItem() {
        /* Удаляем товар и переходим в главное меню */
        viewModel.deleteItem(item)
        findNavController().navigateUp()
    }

    /**
     * Called when fragment is destroyed.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
