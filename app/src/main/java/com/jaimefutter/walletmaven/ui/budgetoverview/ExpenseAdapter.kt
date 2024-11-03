package com.jaimefutter.walletmaven.ui.budgetoverview

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jaimefutter.walletmaven.ApiResponse
import com.jaimefutter.walletmaven.ExpenseEntity
import com.jaimefutter.walletmaven.R
import com.jaimefutter.walletmaven.WalletMavenAPIService
import com.jaimefutter.walletmaven.roomdb.CategoryDao
import com.jaimefutter.walletmaven.roomdb.ExpenseDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ExpenseAdapter(
    private var expenses: MutableList<ExpenseEntity>,
    private val userID: String,
    private val apiService: WalletMavenAPIService,
    private val context: Context,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>()
{
    private val categoryIcons = intArrayOf(
        R.drawable.ic_bank_fees,
        R.drawable.ic_bills_utilities,
        R.drawable.ic_clothing,
        R.drawable.ic_dining_out,
        R.drawable.ic_education,
        R.drawable.ic_entertainment,
        R.drawable.ic_fitness,
        R.drawable.ic_gifts,
        R.drawable.ic_groceries,
        R.drawable.ic_health,
        R.drawable.ic_household,
        R.drawable.ic_insurance,
        R.drawable.ic_internet,
        R.drawable.ic_investments,
        R.drawable.ic_bank_fees,
        R.drawable.ic_medical,
        R.drawable.ic_mortgage,
        R.drawable.ic_pet_care,
        R.drawable.ic_phone,
        R.drawable.ic_savings,
        R.drawable.ic_shopping,
        R.drawable.ic_subscriptions,
        R.drawable.ic_bank_fees,
        R.drawable.ic_transportation,
        R.drawable.ic_vacation,
        R.drawable.ic_vehicle_maintenance
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder
    {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.expense_card, parent, false) // Replace with your card layout resource
        return ExpenseViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int)
    {
        val expense = expenses[position]

        // Bind data
        holder.storeNameTextView.text = expense.storeName
        holder.categoryNameTextView.text = expense.category

        // Get the currency symbol from SharedPreferences using the holder's itemView context
        val sharedPref = holder.itemView.context.getSharedPreferences(
            "app_preferences",
            AppCompatActivity.MODE_PRIVATE
        )
        val currencySymbol = sharedPref.getString("currency_symbol", "R") ?: "R" // Default to "R"
        holder.priceTextView.text = currencySymbol + " " + expense.price.toString()

        // Original date string from expense
        val originalDate = expense.date

        // Convert the original date to a more readable format
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        try
        {
            val parsedDate = inputFormat.parse(originalDate)
            val formattedDate = outputFormat.format(parsedDate ?: "")
            holder.dateTextView.text = formattedDate
        } catch (e: Exception)
        {
            // In case parsing fails, display the original date
            holder.dateTextView.text = originalDate.substringBefore("T")
        }

        // Set category icon based on category
        holder.categoryIconView.setImageResource(getCategoryIcon(expense.category))

        // Handle the delete button click
        holder.deleteButton.setOnClickListener {
            confirmDeleteExpense(expense, userID, position)
        }

        // Set click listener for the CardView to show the image
        holder.itemView.setOnClickListener {
            Log.d("TEST", expense.imageUrl.toString())
            if (!expense.imageUrl.isNullOrEmpty())
            {
                // Create an ImageView to display the image
                val imageView = ImageView(context)

                // Using Glide to load the image
                Glide.with(context)
                    .load(expense.imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(imageView)

                // Create the dialog
                AlertDialog.Builder(context)
                    .setTitle("Expense Image")
                    .setView(imageView)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            } else
            {
                // Show a toast message if the imageUrl is null or empty
                Toast.makeText(context, "There's no picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = expenses.size

    private fun confirmDeleteExpense(expense: ExpenseEntity, userID: String, position: Int)
    {
        // Create an AlertDialog builder
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Delete Expense")
        builder.setMessage("Are you sure you want to delete '${expense.storeName}'?")

        // Set up the positive (Yes) button
        builder.setPositiveButton("Yes") { dialog, _ ->
            // Call the deleteExpense method if user confirms
            deleteExpense(expense, userID, position)
            dialog.dismiss()
        }

        // Set up the negative (No) button
        builder.setNegativeButton("No") { dialog, _ ->
            // Dismiss the dialog if user cancels
            dialog.dismiss()
        }

        // Show the AlertDialog
        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun deleteExpense(expense: ExpenseEntity, userID: String, position: Int)
    {
        // Make API call to delete the expense
        val deleteCall = apiService.deleteExpense(expense.documentId, userID)

        deleteCall.enqueue(object : Callback<ApiResponse>
        {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>)
            {
                if (response.isSuccessful)
                {
                    // Get the category of the deleted expense
                    val deletedExpenseCategory = expenses[position].category

                    // Launch a coroutine to delete the expense from Room
                    CoroutineScope(Dispatchers.IO).launch {
                        // Delete the expense by ID
                        expenseDao.deleteExpenseByFields(
                            expense.storeName,
                            expense.category,
                            expense.price,
                            expense.date,
                            expense.userID
                        )

                        // Update the UI on the main thread after deletion
                        withContext(Dispatchers.Main) {
                            // Remove the expense from the list
                            expenses.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, expenses.size)

                            // Check if there are no more expenses in the deleted category
                            val isCategoryEmpty =
                                expenses.none { it.category == deletedExpenseCategory }

                            if (isCategoryEmpty)
                            {
                                // Call the function to delete the empty category
                                deleteCategory(deletedExpenseCategory, userID)
                                categoryDao.deleteCategory(deletedExpenseCategory)
                            }
                        }
                    }
                } else
                {
                    Log.e(
                        "ExpenseAdapter",
                        "Failed to delete expense: ${response.errorBody()?.string()}"
                    )
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable)
            {
                Log.e("ExpenseAdapter", "API call failed: ${t.message}")
            }
        })
    }

    private fun deleteCategory(categoryName: String, userID: String)
    {
        // Call your API endpoint to delete the category
        val deleteCategoryCall = apiService.deleteCategory(categoryName, userID)

        deleteCategoryCall.enqueue(object : Callback<ApiResponse>
        {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>)
            {
                if (response.isSuccessful)
                {
                    Log.d("ExpenseAdapter", "Category deleted successfully: $categoryName")
                    // Additional logic if needed after successful deletion
                } else
                {
                    // Handle error response and log the BadRequest message
                    val errorMessage = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("ExpenseAdapter", "Failed to delete category: $errorMessage")
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable)
            {
                Log.e("ExpenseAdapter", "Category delete API call failed: ${t.message}")
            }
        })
    }

    inner class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    {
        val storeNameTextView: TextView = itemView.findViewById(R.id.storeNameTextView)
        val categoryNameTextView: TextView = itemView.findViewById(R.id.categoryNameTextView)
        val categoryIconView: ImageView = itemView.findViewById(R.id.categoryIconView)
        val priceTextView: TextView = itemView.findViewById(R.id.priceTextView)
        val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    // Method to update the adapter's data
    fun updateExpenses(newExpenses: List<ExpenseEntity>)
    {
        expenses.clear()
        expenses.addAll(newExpenses)
        notifyDataSetChanged()
    }

    private fun getCategoryIcon(category: String): Int {
        // Get the array of category names from resources
        val categoryNames = context.resources.getStringArray(R.array.budget_categories)

        // Find the index of the selected category
        val categoryIndex = categoryNames.indexOf(category)

        // Return the corresponding icon or a default icon if the category is not found
        return if (categoryIndex >= 0 && categoryIndex < categoryIcons.size) {
            categoryIcons[categoryIndex]
        } else {
            R.drawable.ic_default_category // Default icon for unknown categories
        }
    }

}