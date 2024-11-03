package com.jaimefutter.walletmaven.ui.budgetoverview

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.jaimefutter.walletmaven.ExpenseEntity
import com.jaimefutter.walletmaven.ExpenseResponse
import com.jaimefutter.walletmaven.R
import com.jaimefutter.walletmaven.RetrofitClient
import com.jaimefutter.walletmaven.WalletMavenAPIService
import com.jaimefutter.walletmaven.databinding.FragmentBudgetoverviewBinding
import com.jaimefutter.walletmaven.roomdb.CategoryDao
import com.jaimefutter.walletmaven.roomdb.ExpenseDao
import com.jaimefutter.walletmaven.roomdb.WalletMavenDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class BudgetOverviewFragment : Fragment()
{
    private var userID: String? = null
    private var _binding: FragmentBudgetoverviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var expenseList: MutableList<ExpenseEntity>
    private var expensesCall: Call<ExpenseResponse>? = null
    private lateinit var apiService: WalletMavenAPIService
    private lateinit var expenseDao: ExpenseDao
    private lateinit var categoryDao: CategoryDao

    private val TAG = "BudgetOverviewFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View
    {
        _binding = FragmentBudgetoverviewBinding.inflate(inflater, container, false)
        val root: View = binding.root

        recyclerView = binding.recyclerViewBudget
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Initialize the API service
        apiService = RetrofitClient.instance.create(WalletMavenAPIService::class.java)

        val currentUser = FirebaseAuth.getInstance().currentUser
        userID = currentUser?.uid

        // Initialize ExpenseDao (you need to implement this method)
        val database = WalletMavenDatabase.getDatabase(requireContext())

        expenseDao = database.expenseDao()
        categoryDao = database.categoryDao()

        // Set up AutoCompleteTextView without filtering
        val autoCompleteTextView = binding.autoCompleteTextView2

        // Set an OnItemClickListener to handle dropdown item selections
        autoCompleteTextView.setOnItemClickListener { parent, view, position, id ->
            val selectedCategory = parent.getItemAtPosition(position).toString()
            filterByCategory(selectedCategory) // Call your filtering function
        }

        if (userID != null)
        {
            // Initialize the adapter with the userID and API service
            expenseAdapter = ExpenseAdapter(mutableListOf(), userID!!, apiService, requireContext(), expenseDao, categoryDao)
            recyclerView.adapter = expenseAdapter

            fetchExpenses(userID!!)
        } else
        {
            Log.e(TAG, "User is not logged in.")
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)

        // Now it's safe to use viewLifecycleOwner for observing LiveData
        viewLifecycleOwner.lifecycleScope.launch {
            fetchExpensesFromLocalDB(requireContext(), userID!!)
        }
    }

    private fun filterByCategory(selectedCategory: String)
    {
        if (!::expenseList.isInitialized)
        {
            Log.e(TAG, "Expense list not initialized")
            return
        }

        val filteredList =
            if (selectedCategory.equals(getString(R.string.all), ignoreCase = true) || selectedCategory.isEmpty())
            {
                expenseList // Show all items if "All" is selected or if the search query is empty
            } else
            {
                expenseList.filter { expense ->
                    expense.category.equals(selectedCategory, ignoreCase = true)
                }
            }

        Log.d(TAG, "Filtered expenses count: ${filteredList.size}")
        // Update the adapter with the filtered list
        expenseAdapter.updateExpenses(filteredList)
    }

    private fun fetchExpenses(userID: String)
    {
        expensesCall = apiService.getExpenses(userID)
        expensesCall?.enqueue(object : Callback<ExpenseResponse>
        {
            override fun onResponse(
                call: Call<ExpenseResponse>,
                response: Response<ExpenseResponse>
            )
            {
                Log.d(TAG, "Fetching expenses for userID: $userID")

                if (response.isSuccessful)
                {
                    response.body()?.data?.let { expenses ->
                        Log.d(TAG, "Fetched Expenses: $expenses")
                        updateRecyclerView(expenses.toMutableList()) // Convert to MutableList
                        updateCategoryList(expenses)
                        expenseAdapter.updateExpenses(expenses)
                    } ?: Log.d(TAG, "No expenses found in response")
                } else
                {
                    Log.e(TAG, "API Error: ${response.errorBody()?.string() ?: "Unknown error"}")
                    // Fetch from Room if API call fails
                    viewLifecycleOwner.lifecycleScope.launch {
                        fetchExpensesFromLocalDB(requireContext(), userID)
                    }
                }
            }

            override fun onFailure(call: Call<ExpenseResponse>, t: Throwable)
            {
                Log.e(TAG, "API Failure: ${t.message ?: "Unknown error"}")

                // Fetch from Room if API call fails
                viewLifecycleOwner.lifecycleScope.launch {
                    fetchExpensesFromLocalDB(requireContext(), userID)
                }
            }
        })
    }

    suspend fun fetchExpensesFromLocalDB(context: Context, userID: String) {
        val db = WalletMavenDatabase.getDatabase(context)

        // Fetch expenses on IO dispatcher
        val expenses = withContext(Dispatchers.IO) {
            db.expenseDao().getExpensesByUser(userID)
        }

        if (expenses.isNotEmpty()) {
            val expenseItems = expenses.map { it } // Convert entities to UI items
            updateRecyclerView(expenseItems.toMutableList())
        } else {
            Log.d(TAG, "No expenses available in local database.")
            withContext(Dispatchers.Main) {
                binding.noDataTextView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                Toast.makeText(
                    context,
                    "No offline data available. Please connect to the internet.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateRecyclerView(expenses: MutableList<ExpenseEntity>)
    {
        expenseList = expenses // Initialise expenseList

        if (expenses.isEmpty())
        {
            // Show the "No data available" TextView and hide the RecyclerView
            Log.d(TAG, "No expenses available.")
            binding.noDataTextView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else
        {
            // Hide the "No data available" TextView and show the RecyclerView
            Log.d(TAG, "Updating RecyclerView with expenses.")
            binding.noDataTextView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Update the adapter with the expenses
            expenseAdapter.updateExpenses(expenseList)
        }
    }

    private fun updateCategoryList(expenses: List<ExpenseEntity>)
    {
        // Extract unique categories from expenses
        val uniqueCategories = expenses.mapNotNull { it.category.takeIf { it.isNotBlank() } }
            .distinct()
            .toMutableList()

        // Add "All" as the first option
        uniqueCategories.add(0, getString(R.string.all))

        Log.d(TAG, "Unique Categories with 'All': $uniqueCategories")

        if (uniqueCategories.size == 1 && uniqueCategories[0] == getString(R.string.all))
        {
            binding.autoCompleteTextView2.setText(
                getString(R.string.none),
                false
            ) // Set "None" as the default selection
            binding.autoCompleteTextView2.setAdapter(null)
            Log.d(TAG, "No unique categories found.")
        } else
        {
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                uniqueCategories
            )
            binding.autoCompleteTextView2.setAdapter(adapter)
            binding.autoCompleteTextView2.setText(
                getString(R.string.all),
                false
            ) // Set "All" as the default selection
        }

    }

    override fun onDestroyView()
    {
        super.onDestroyView()
        // Cancel the ongoing Retrofit call if needed
        expensesCall?.cancel()
        _binding = null
    }
}
