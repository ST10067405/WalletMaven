package com.jaimefutter.walletmaven.ui.dashboard

import com.jaimefutter.walletmaven.roomdb.SyncWorker
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.icu.text.DecimalFormat
import android.icu.text.DecimalFormatSymbols
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.jaimefutter.walletmaven.CategoryEntity
import com.jaimefutter.walletmaven.ExpenseEntity
import com.jaimefutter.walletmaven.R
import com.jaimefutter.walletmaven.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    lateinit var binding: FragmentDashboardBinding
    lateinit var viewModel: DashboardViewModel
    lateinit var categories: List<CategoryEntity>
    lateinit var expenses: List<ExpenseEntity>
    var budgetUsage: Map<String, Pair<Double, Double>> = emptyMap()
    private val TAG = "DashboardFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Creating dashboard view")
        binding = FragmentDashboardBinding.inflate(inflater, container, false)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)

        observeViewModel()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: View created")

        // Display "Loading..." while fetching data
        binding.btnViewReports.text = getString(R.string.loading)
        binding.btnViewReports.isEnabled = false

        // Trigger data loading
        loadExpensesAndCategories()

        binding.btnViewReports.setOnClickListener {
            showBudgetInfo()
        }

        // Schedule sync worker
        scheduleSyncWorker()
    }

    private fun observeViewModel() {
        var expensesReady = false
        var categoriesReady = false

        // Observe expenses and categories
        viewModel.expenses.observe(viewLifecycleOwner) { fetchedExpenses ->
            expenses = fetchedExpenses
            expensesReady = true
            if (categoriesReady) {
                displayChart(expenses, categories)
            }
        }

        viewModel.categories.observe(viewLifecycleOwner) { fetchedCategories ->
            categories = fetchedCategories
            categoriesReady = true
            if (expensesReady) {
                displayChart(expenses, categories)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnViewReports.text = if (isLoading) getString(R.string.loading) else getString(R.string.view_reports)
            binding.btnViewReports.isEnabled = !isLoading
        }

        // If theres nothing in the lists/db
        viewModel.isButtonEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.btnViewReports.isEnabled = isEnabled
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadExpensesAndCategories() {
        val userID = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        Log.d(TAG, "loadExpenses: Checking network status...")

        if (isNetworkAvailable(requireContext())) {
            // Fetch data from API if the network is available
            Log.d(TAG, "loadExpenses: Network available, fetching data from API.")
            viewModel.fetchDataFromAPI(requireContext(), userID)
        } else {
            // Fetch data from RoomDB if offline
            Toast.makeText(context, "No internet connection. Loading data from offline storage...", Toast.LENGTH_LONG).show()
            Log.d(TAG, "loadExpenses: No network available, fetching data from RoomDB.")
            viewModel.fetchDataFromLocalDB(requireContext(), userID)
        }
    }

    private fun scheduleSyncWorker() {
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .build()

        WorkManager.getInstance(requireContext())
            .enqueueUniqueWork(
                "SyncWorker", // Unique name for this worker
                ExistingWorkPolicy.KEEP, // Keep the existing work if it's already running
                syncWorkRequest
            )
    }

    fun displayChart(expenses: List<ExpenseEntity>, categories: List<CategoryEntity>) {
        Log.d(TAG, "displayChart: Displaying chart with ${expenses.size} expenses and ${categories.size} categories")

        // Retrieve the currency symbol from SharedPreferences
        val preferences = requireActivity().getSharedPreferences(
            "app_preferences",
            AppCompatActivity.MODE_PRIVATE
        )
        val currencySymbol = preferences.getString("currency_symbol", "R") ?: "R" // Default to "R"

        val entries = expenses.groupBy { it.category }.map { (category, expenseList) ->
            // Modify the category label if necessary for wrapping
            PieEntry(expenseList.sumOf { it.price }.toFloat(), category)
        }

        // Create a PieDataSet
        val dataSet = PieDataSet(entries, "Expenses").apply {
            colors = ColorTemplate.COLORFUL_COLORS.toList() // Use colorful preset colors
            valueTextSize = 14f // Set the text size for the pie chart values
            valueFormatter =
                CurrencyValueFormatter(currencySymbol) // Use the custom value formatter
        }

        // Configure PieChart
        binding.pieChart.setHoleRadius(40f) // Adjust the hole radius
        binding.pieChart.setDrawHoleEnabled(true) // Enable hole drawing
        binding.pieChart.setHoleColor(Color.TRANSPARENT) // Set the hole color to transparent
        binding.pieChart.setTransparentCircleColor(Color.TRANSPARENT) // Optional: set transparent circle color
        binding.pieChart.setTransparentCircleAlpha(0) // Remove alpha effect around the hole

        // Shadow customization
        binding.pieChart.setLayerType(View.LAYER_TYPE_SOFTWARE, null) // Enable software layer for shadows
        binding.pieChart.setBackgroundColor(Color.TRANSPARENT) // Set background to transparent

        // Configure Legend
        binding.pieChart.legend.isEnabled = false
        binding.pieChart.description.isEnabled = false

        // Calculate budget usage
        budgetUsage = categories.associate { category ->
            val usedAmount = expenses.filter { it.category == category.name }
                .sumOf { it.price }
            category.name to Pair(category.budgetLimit, usedAmount)
        }

        // Set the pie data and refresh the chart
        val pieData = PieData(dataSet)
        binding.pieChart.data = pieData
        binding.pieChart.notifyDataSetChanged() // Notify chart of data changes
        binding.pieChart.invalidate()
        Log.d(TAG, "displayChart: Chart updated with pie data")
    }

    fun showBudgetInfo() {
        Log.d(TAG, "showBudgetInfo: Showing budget information")
        val budgetInfo = StringBuilder()

        // Get the currency symbol from SharedPreferences
        val sharedPref = requireActivity().getSharedPreferences(
            "app_preferences",
            AppCompatActivity.MODE_PRIVATE
        )
        val currencySymbol = sharedPref.getString("currency_symbol", "R") ?: "R" // Default to "R"

        // Create a DecimalFormat instance for currency formatting
        val decimalFormatSymbols = DecimalFormatSymbols().apply {
            groupingSeparator = ' ' // Set space as the grouping separator
            decimalSeparator = '.' // Set period as the decimal separator
        }
        val decimalFormat = DecimalFormat("#,##0.00", decimalFormatSymbols)

        budgetUsage.forEach { (category, amounts) ->
            val budgetLimitFormatted = "$currencySymbol${decimalFormat.format(amounts.first)}"
            val usedFormatted = "$currencySymbol${decimalFormat.format(amounts.second)}"
            val remainingFormatted =
                "$currencySymbol${decimalFormat.format(amounts.first - amounts.second)}"

            budgetInfo.append(
                "${category}:\n" +
                        "\t${getString(R.string.buget_limit)}:\t\t$budgetLimitFormatted\n" +
                        "\t${getString(R.string.used)}:\t\t\t\t\t\t\t\t\t\t\t\t$usedFormatted\n" +
                        "\t${getString(R.string.remaining)}:\t\t\t\t$remainingFormatted\n\n"
            )
        }

        // Create a TextView to display the budget info
        val textView = TextView(requireContext()).apply {
            text = budgetInfo.toString()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(100, 32, 32, 32) // Add some padding to the text
        }

        // Wrap the TextView in a ScrollView
        val scrollView = ScrollView(requireContext()).apply {
            addView(textView)
            setPadding(16, 16, 16, 16) // Optional: Add padding around the ScrollView
            isVerticalScrollBarEnabled = true
        }

        // Set a max height for the ScrollView
        val maxHeight = 600 // Adjust the max height as needed (in pixels)
        scrollView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            maxHeight
        )

        // Create a custom TextView for the dialog title
        val customTitle = TextView(requireContext()).apply {
            text = context.getString(R.string.budget_information)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f) // Change title text size here
            setTypeface(null, Typeface.BOLD) // Make the title bold
            setPadding(40, 40, 40, 20) // Adjust padding as needed
        }

        // Create and display the MaterialAlertDialog
        MaterialAlertDialogBuilder(requireContext())
            .setCustomTitle(customTitle)
            .setView(scrollView) // Set the ScrollView as the view for the dialog
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
