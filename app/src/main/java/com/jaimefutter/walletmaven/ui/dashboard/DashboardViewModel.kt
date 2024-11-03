package com.jaimefutter.walletmaven.ui.dashboard

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.Keep
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaimefutter.walletmaven.CategoryEntity
import com.jaimefutter.walletmaven.CategoryResponse
import com.jaimefutter.walletmaven.ExpenseEntity
import com.jaimefutter.walletmaven.ExpenseResponse
import com.jaimefutter.walletmaven.RetrofitClient
import com.jaimefutter.walletmaven.WalletMavenAPIService
import com.jaimefutter.walletmaven.roomdb.WalletMavenDatabase
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardViewModel : ViewModel() {

    private val _expenses = MutableLiveData<List<ExpenseEntity>>()
    val expenses: LiveData<List<ExpenseEntity>> = _expenses

    private val _categories = MutableLiveData<List<CategoryEntity>>()
    val categories: LiveData<List<CategoryEntity>> = _categories

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    val isButtonEnabled: MediatorLiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(_isLoading) { loading ->
            value = !loading && (_categories.value?.isNotEmpty() == true || _expenses.value?.isNotEmpty() == true)
        }
        addSource(_categories) { _ ->
            value = !_isLoading.value!! && (_categories.value?.isNotEmpty() == true || _expenses.value?.isNotEmpty() == true)
        }
        addSource(_expenses) { _ ->
            value = !_isLoading.value!! && (_categories.value?.isNotEmpty() == true || _expenses.value?.isNotEmpty() == true)
        }
    }

    private val TAG = "DashboardViewModel"


    fun fetchDataFromAPI(context: Context, userID: String) {
        Log.d(TAG, "fetchDataFromAPI: Starting API call for userID: $userID")
        _isLoading.value = true

        val apiService = RetrofitClient.instance.create(WalletMavenAPIService::class.java)

        // Fetch categories
        apiService.getCategories(userID).enqueue(object : Callback<CategoryResponse> {
            override fun onResponse(call: Call<CategoryResponse>, response: Response<CategoryResponse>) {
                if (response.isSuccessful) {
                    Log.d(TAG, "fetchDataFromAPI: Successfully fetched categories from API.")
                    _categories.value = response.body()?.data ?: emptyList()
                    fetchExpenses(context, userID, apiService)
                } else {
                    _isLoading.value = false
                    _errorMessage.value = "Failed to retrieve categories"
                    Log.e(TAG, "fetchDataFromAPI: Failed to retrieve categories from API. Response code: ${response.code()}")
                    Toast.makeText(context, "Failed to retrieve categories from server.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<CategoryResponse>, t: Throwable) {
                _isLoading.value = false
                _errorMessage.value = "Error: ${t.message}"
                Log.e(TAG, "fetchDataFromAPI: Error while fetching categories from API. Error: ${t.message}")
                Toast.makeText(context, "Error fetching data from server: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchExpenses(context: Context, userID: String, apiService: WalletMavenAPIService) {
        Log.d(TAG, "fetchExpenses: Fetching expenses from API for userID: $userID")
        apiService.getExpenses(userID).enqueue(object : Callback<ExpenseResponse> {
            override fun onResponse(call: Call<ExpenseResponse>, response: Response<ExpenseResponse>) {
                _isLoading.value = false
                if (response.isSuccessful) {
                    Log.d(TAG, "fetchExpenses: Successfully fetched expenses from API.")
                    _expenses.value = response.body()?.data ?: emptyList()
                } else {
                    Log.e(TAG, "fetchExpenses: Failed to retrieve expenses from API. Response code: ${response.code()}")
                    _errorMessage.value = "Failed to retrieve expenses"
                }
            }

            override fun onFailure(call: Call<ExpenseResponse>, t: Throwable) {
                _isLoading.value = false
                _errorMessage.value = "Error: ${t.message}"
                Log.e(TAG, "fetchExpenses: Error while fetching expenses from API. Error: ${t.message}")
                Toast.makeText(context, "Error fetching expenses from server: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun fetchDataFromLocalDB(context: Context, userID: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val db = WalletMavenDatabase.getDatabase(context)

            // Fetch expenses
            val expenses = db.expenseDao().getExpensesByUser(userID)
            if (expenses.isNotEmpty()) {
                _expenses.value = expenses.map { it }
            } else {
                Log.e(TAG, "fetchDataFromLocalDB: No expenses found in RoomDB for userID: $userID.")
                _errorMessage.value = "No data available offline."
                Toast.makeText(context, "No offline data available. Please connect to the internet.", Toast.LENGTH_LONG).show()
            }

            // Fetch categories
            val categories = db.categoryDao().getCategoriesByUser(userID)
            if (categories.isNotEmpty()) {
                Log.d(TAG, "fetchDataFromLocalDB: Successfully fetched ${categories.size} categories from RoomDB.")
                _categories.value = categories.map { it } // Ensure you have a method to map to CategoryItem
            } else {
                Log.e(TAG, "fetchDataFromLocalDB: No categories found in RoomDB for userID: $userID.")
                // Optionally handle no categories case
                _errorMessage.value = "No categories available offline."
            }

            // Notify loading status
            if (_expenses.value != null || _categories.value != null) {
                _isLoading.value = false // Indicate that loading is complete
                Toast.makeText(context, "Loaded data from offline storage.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

