package com.example.inventory.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.NumberFormat

/* Класс товара (был в гайде) */
@Entity(tableName = "item")
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "name")
    val itemName: String,
    @ColumnInfo(name = "price")
    val itemPrice: Double,
    @ColumnInfo(name = "quantity")
    val quantityInStock: Int,
    /* Добавленные поля для задания */
    @ColumnInfo(name = "providerName")
    var providerName: String,
    @ColumnInfo(name = "providerEmail")
    var providerEmail: String,
    @ColumnInfo(name = "providerPhone")
    var providerPhone : String,
    @ColumnInfo(name = "record")
    var record: Record = Record.MANUAL
) {
    /* Метод для красивого отображения данных в "поделиться" */
    override fun toString() : String {
        return "Название товара: ${itemName}\n" +
                "Цена: ${itemPrice}\n" +
                "Количество: ${quantityInStock}\n" +
                "Поставщик: ${providerName}\n" +
                "Email Постовщика: ${providerEmail}\n" +
                "Номер телефона поставщика: $providerPhone"
    }
}

fun Item.getFormattedPrice(): String =
    NumberFormat.getCurrencyInstance().format(itemPrice)

enum class Record{
    MANUAL, FILE
}