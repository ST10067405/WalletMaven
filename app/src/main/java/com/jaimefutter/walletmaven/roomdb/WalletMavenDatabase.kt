package com.jaimefutter.walletmaven.roomdb

import android.content.Context
import androidx.annotation.Keep
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jaimefutter.walletmaven.CategoryEntity
import com.jaimefutter.walletmaven.ExpenseEntity

@Database(entities = [ExpenseEntity::class, CategoryEntity::class], version = 2)
abstract class WalletMavenDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: WalletMavenDatabase? = null

        fun getDatabase(context: Context): WalletMavenDatabase {
            // If the instance is null, create a new database instance
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WalletMavenDatabase::class.java,
                    "walletmaven-db" // Ensure the name is consistent
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}