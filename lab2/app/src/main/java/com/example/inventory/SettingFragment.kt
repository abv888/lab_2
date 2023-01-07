package com.example.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.inventory.databinding.FragmentSettingBinding


class SettingFragment: Fragment() {
    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    private val PREFS_FILE = "Setting"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* Создаём ключ шифрования (из оф. документации) */
        val masterKeyAlias = MasterKey.Builder(requireContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        /* Зашифровывает/расшифровывает настройки приложения (из оф. документации) */
        /* Что-то типа реестра на ПК, только для android */
        val sharedPreferences = EncryptedSharedPreferences.create(
            requireContext(),
            PREFS_FILE,
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        binding.apply {
            /* Если есть поля в "реестре", то расшифровываем и заполняем соответствующие элементы */
            defaultProviderName.setText(sharedPreferences.getString("ProviderName",""))
            defaultProviderEmail.setText(sharedPreferences.getString("ProviderEmail",""))
            defaultProviderPhone.setText(sharedPreferences.getString("ProviderPhone",""))
            checkBoxDefault.isChecked = sharedPreferences.getBoolean("CheckBoxDefault",false)
            checkBoxHide.isChecked = sharedPreferences.getBoolean("CheckBoxHide",false)
            checkBoxForbid.isChecked = sharedPreferences.getBoolean("CheckBoxForbid",false)

            saveAction.setOnClickListener {
                /* По нажатию на кнопку шифруем и сохраняем данные в "реестре" */
                sharedPreferences.edit().apply {
                    putString("ProviderName", defaultProviderName.text.toString())
                    putString("ProviderEmail", defaultProviderEmail.text.toString())
                    putString("ProviderPhone", defaultProviderPhone.text.toString())
                    putBoolean("CheckBoxDefault", checkBoxDefault.isChecked)
                    putBoolean("CheckBoxHide", checkBoxHide.isChecked)
                    putBoolean("CheckBoxForbid", checkBoxForbid.isChecked)
                }.apply()

                /* И переходим на главную страницу */
                val action = SettingFragmentDirections.actionSettingFragmentToItemListFragment()
                findNavController().navigate(action)
            }
        }


    }

}