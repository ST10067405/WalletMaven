package com.jaimefutter.walletmaven

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

data class ExpenseResponse(
    val message: String,
    val data: List<ExpenseEntity>
)

data class CategoryResponse(
    val message: String,
    val data: List<CategoryEntity>
)

@Entity(tableName = "expense")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val storeName: String,
    val price: Double,
    val category: String,
    val date: String,
    val userID: String,
    val imageUrl: String?, // Nullable field for optional image
    val isSynced: Boolean = false,
    val documentId: String
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val budgetLimit: Double,
    val userID: String,
    val isSynced: Boolean = false,
    val documentId: String
)