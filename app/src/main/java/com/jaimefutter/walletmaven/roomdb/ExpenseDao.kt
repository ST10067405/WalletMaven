package com.jaimefutter.walletmaven.roomdb

import androidx.annotation.Keep
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import androidx.room.*
import com.jaimefutter.walletmaven.CategoryEntity
import com.jaimefutter.walletmaven.ExpenseEntity

@Dao
interface ExpenseDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expense WHERE storeName = :storeName AND category = :category AND price = :price AND date = :date AND userID = :userID")
    suspend fun deleteExpenseByFields(storeName: String, category: String, price: Double, date: String, userID: String)

    @Query("DELETE FROM expense WHERE documentId = :expenseId")
    suspend fun deleteExpenseById(expenseId: String)

    @Query("SELECT * FROM expense")
    suspend fun getAllExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expense WHERE userID = :userID")
    suspend fun getExpensesByUser(userID: String): List<ExpenseEntity>

    @Query("SELECT * FROM expense WHERE userID = :userID AND isSynced = 0")
    suspend fun getUnsyncedExpenses(userID: String): List<ExpenseEntity>

    @Query("SELECT * FROM expense WHERE userId = :userId")
    fun getExpensesByUserLD(userId: String): LiveData<List<ExpenseEntity>>
}

@Dao
interface CategoryDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(category: List<CategoryEntity>)

    @Query("DELETE FROM category WHERE name = :categoryName")
    suspend fun deleteCategory(categoryName: String)

    @Query("SELECT * FROM category")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE userID = :userID")
    suspend fun getCategoriesByUser(userID: String): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE userID = :userID AND isSynced = 0")
    suspend fun getUnsyncedCategories(userID: String): List<CategoryEntity>
}
