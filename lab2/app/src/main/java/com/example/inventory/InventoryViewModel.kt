package com.example.inventory

import androidx.lifecycle.*
import com.example.inventory.data.Item
import com.example.inventory.data.ItemDao
import com.example.inventory.data.Record
import kotlinx.coroutines.launch

class InventoryViewModel(private val itemDao: ItemDao): ViewModel() {

    val allItems: LiveData<List<Item>> = itemDao.getItems().asLiveData()

    /* Метод для добавления элементов в БД */
    private fun insertItem(item: Item) {
        viewModelScope.launch {
            itemDao.insert(item)
        }
    }

    /* Метод возврящает данные с правильными типа данных */
    private fun getNewItemEntry(itemName: String, itemPrice: String, itemCount: String,
                                itemProviderName: String, itemProviderEmail: String, itemProviderPhone: String): Item {
        return Item(
            itemName = itemName,
            itemPrice = itemPrice.toDouble(),
            quantityInStock = itemCount.toInt(),
            providerName = itemProviderName,
            providerEmail = itemProviderEmail,
            providerPhone = itemProviderPhone
        )
    }

    /* Добавление элемента из файла */
    fun addNewItem(item: Item) {
        item.record = Record.FILE
        insertItem(item)
    }

    /* Добавление элемента из формы */
    fun addNewItem(itemName: String, itemPrice: String, itemCount: String,
                   itemProviderName: String, itemProviderEmail: String, itemProviderPhone: String) {
        val newItem = getNewItemEntry(itemName, itemPrice, itemCount, itemProviderName, itemProviderEmail, itemProviderPhone)
        insertItem(newItem)
    }

    /* Проверка на валидацию данных */
    fun isEntryValid(itemName: String, itemPrice: String, itemCount: String, itemProviderName: String, itemProviderEmail: String, itemProviderPhone: String): Boolean {
        if(itemName.isBlank() ||
            itemPrice.isBlank() ||
            itemCount.isBlank() ||
            itemProviderName.isBlank() ||
            itemProviderEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(itemProviderEmail).matches() ||
            itemProviderPhone.isBlank() || !android.util.Patterns.PHONE.matcher(itemProviderPhone).matches()) {
            return false
        }
        return true
    }

    /* Получаем данных из БД */
    fun retrieveItem(id: Int): LiveData<Item> {
        return itemDao.getItem(id).asLiveData()
    }

    /* Обновить данные уже существующего товара */
    private fun updateItem(item: Item) {
        viewModelScope.launch {
            itemDao.update(item)
        }
    }

    /* Уменьшаем количество товаров */
    fun sellItem(item: Item) {
        if (item.quantityInStock > 0) {
            val newItem = item.copy(quantityInStock = item.quantityInStock - 1)
            updateItem(newItem)
        }
    }

    /* Проверка, что количество больше 0*/
    fun isStockAvailable(item: Item): Boolean {
        return (item.quantityInStock > 0)
    }

    /* Удаоение товара из БД */
    fun deleteItem(item: Item) {
        viewModelScope.launch {
            itemDao.delete(item)
        }
    }

    /* Что-то типа конструктора класса */
    private fun getUpdatedItemEntry(
        itemId: Int,
        itemName: String,
        itemPrice: String,
        itemCount: String,
        itemProviderName: String,
        itemProviderEmail: String,
        itemProviderPhone: String
    ): Item {
        return Item(
            id = itemId,
            itemName = itemName,
            itemPrice = itemPrice.toDouble(),
            quantityInStock = itemCount.toInt(),
            providerName = itemProviderName,
            providerEmail = itemProviderEmail,
            providerPhone = itemProviderPhone
        )
    }

    /* Обновление товара из БД */
    fun updateItem(
        itemId: Int,
        itemName: String,
        itemPrice: String,
        itemCount: String,
        itemProviderName: String,
        itemProviderEmail: String,
        itemProviderPhone: String
    ) {
        val updatedItem = getUpdatedItemEntry(itemId, itemName, itemPrice, itemCount,
            itemProviderName, itemProviderEmail, itemProviderPhone)
        updateItem(updatedItem)
    }
}

/*Какая-то неведомая штука, было в гайде */
class InventoryViewModelFactory(private val itemDao: ItemDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InventoryViewModel(itemDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}