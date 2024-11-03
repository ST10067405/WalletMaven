package com.jaimefutter.walletmaven

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.jaimefutter.walletmaven.MyBiometricManager.Callback.Companion.AUTHENTICATION_ERROR
import com.jaimefutter.walletmaven.MyBiometricManager.Callback.Companion.AUTHENTICATION_FAILED
import com.jaimefutter.walletmaven.MyBiometricManager.Callback.Companion.AUTHENTICATION_SUCCESSFUL
import com.jaimefutter.walletmaven.databinding.ActivityMainBinding
import com.jaimefutter.walletmaven.databinding.AddExpenseDialogBinding
import com.jaimefutter.walletmaven.roomdb.CategoryDao
import com.jaimefutter.walletmaven.roomdb.ExpenseDao
import com.jaimefutter.walletmaven.roomdb.SyncWorker
import com.jaimefutter.walletmaven.roomdb.WalletMavenDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity(), MyBiometricManager.Callback
{
    private lateinit var dialogBinding: AddExpenseDialogBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var currentUser: FirebaseUser? = null
    private var selectedImageUri: Uri? = null
    private lateinit var currentPhotoPath: String
    private lateinit var expenseDialog: AlertDialog
    private var myBiometricManager: MyBiometricManager? = null
    private lateinit var walletMavenDatabase: WalletMavenDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var preferences: SharedPreferences
    private var failedAttempts = 0 // Counter for failed attempts
    private val maxAttempts = 5 // Maximum allowed attempts
    private val TAG = "MainActivity" // Tag for logging

    override fun onCreate(savedInstanceState: Bundle?)
    {
        // Initialize SharedPreferences
        preferences = getSharedPreferences("app_preferences", MODE_PRIVATE)

        // Set Locale based on saved preferences before setting the content view
        val currentLanguageCode = preferences.getString("selected_language", "en") ?: "en"
        setLocale(currentLanguageCode)

        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Initializing MainActivity")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        Log.d(TAG, "onCreate: Firebase Auth initialized")

        // Check if the user is logged in
        currentUser = auth.currentUser
        if (currentUser == null)
        {
            Log.d(TAG, "onCreate: No user is signed in, redirecting to Login activity")
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            finish()
        } else
        {
            Log.d(TAG, "onCreate: User is signed in: ${currentUser?.uid}")

            Log.d(TAG, "onCreate: Initiating Biometrics for ${currentUser?.displayName}")
            myBiometricManager = MyBiometricManager.getInstance(this)
            // Automatically initiate biometric authentication
            if (myBiometricManager!!.checkIfBiometricFeatureAvailable())
            {
                myBiometricManager!!.authenticate()
            }


            val navView: BottomNavigationView = binding.navView
            val fab: FloatingActionButton = binding.fab

            navController = findNavController(R.id.nav_host_fragment_activity_main)
            val appBarConfiguration = AppBarConfiguration(
                setOf(R.id.navigation_dashboard, R.id.navigation_budgetoverview)
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)


            // Initialize Room Database
            walletMavenDatabase = WalletMavenDatabase.getDatabase(this)
            Log.d(TAG, "onCreate: Room Database initialized")

            // Initialize DAO
            expenseDao = walletMavenDatabase.expenseDao()
            categoryDao = walletMavenDatabase.categoryDao()


            // Check notification permission for Android 13 (API 33) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            {
                if (ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                )
                {
                    // Request the notification permission
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    )
                }
            }

            // Schedule sync worker
            scheduleSyncWorker()

            // Set up FAB click listener
            fab.setOnClickListener {
                Log.d(TAG, "FAB clicked, showing popup menu")
                val popupMenu = PopupMenu(this, it)
                popupMenu.menuInflater.inflate(R.menu.fab_menu, popupMenu.menu)
                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId)
                    {
                        R.id.add_category ->
                        {
                            Log.d(TAG, "FAB Menu: Add Category selected")
                            showAddCategoryDialog()
                        }

                        R.id.add_expense ->
                        {
                            Log.d(TAG, "FAB Menu: Add Expense selected")
                            showAddExpenseDialog()
                        }
                    }
                    true
                }
                popupMenu.show()
            }
        }
    }

    private fun showAddCategoryDialog()
    {
        Log.d(TAG, "showAddCategoryDialog: Fetching existing categories")
        fetchCategories(currentUser?.uid ?: "") { existingCategories ->
            Log.d(TAG, "showAddCategoryDialog: Existing categories fetched - $existingCategories")
            val dialogView = layoutInflater.inflate(R.layout.add_category_dialog, null)
            val editTextCategoryName =
                dialogView.findViewById<AutoCompleteTextView>(R.id.categoryACTV)
            val editTextBudgetLimit = dialogView.findViewById<TextInputEditText>(R.id.etBudgetLimit)

            val categoryList = resources.getStringArray(R.array.budget_categories).toList()
            val adapter =
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryList)
            editTextCategoryName.setAdapter(adapter)

            MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Add") { _, _ ->
                    val categoryName = editTextCategoryName.text.toString()
                    val budgetLimit = editTextBudgetLimit.text.toString().toDoubleOrNull()

                    if (categoryName.isNotEmpty() && budgetLimit != null)
                    {
                        val count =
                            existingCategories.count { it.equals(categoryName, ignoreCase = true) }
                        Log.d(TAG, "showAddCategoryDialog: Category '$categoryName' count = $count")

                        if (count < 1)
                        {
                            val newCategory = CategoryEntity(
                                name = categoryName,
                                budgetLimit = budgetLimit,
                                userID = currentUser?.uid ?: "",
                                isSynced = false,
                                documentId = "0"
                            )
                            addCategory(newCategory)
                            Toast.makeText(
                                this,
                                "Category '${newCategory.name}' Added",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else
                        {
                            Log.w(
                                TAG,
                                "Attempt to add more than 2 categories with name '$categoryName'"
                            )
                            Toast.makeText(
                                this,
                                "Cannot add more than 2 categories with the name '$categoryName'",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else
                    {
                        Log.w(TAG, "Invalid data provided for category")
                        Toast.makeText(this, "Please enter valid data", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun showAddExpenseDialog()
    {
        Log.d(TAG, "showAddExpenseDialog: Displaying add expense dialog")
        // Use View Binding for the dialog layout
        dialogBinding = AddExpenseDialogBinding.inflate(LayoutInflater.from(this))

        // Get the current user ID
        val currentUser = auth.currentUser
        val userID = currentUser?.uid ?: ""
        Log.d(TAG, "showAddExpenseDialog: Current User ID = $userID")

        fetchCategories(userID) { categories ->
            Log.d(TAG, "showAddExpenseDialog: Fetched categories for dropdown - $categories")

            // Set up the dropdown (Exposed Dropdown Menu)
            val adapter =
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
            dialogBinding.autoCompleteTextView.setAdapter(adapter)

            // Create the dialog
            expenseDialog = MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.root)
                .setPositiveButton("Add") { dialog, _ ->
                    // Handle adding expense
                    val store = dialogBinding.etStoreName.text.toString()
                    val priceValue = dialogBinding.etPrice.text.toString().toDoubleOrNull()
                    val category = dialogBinding.autoCompleteTextView.text.toString()
                    val dateAdded = getCurrentISODate()
                    val imageUrl = selectedImageUri?.toString()

                    Log.d(
                        TAG,
                        "showAddExpenseDialog: Adding expense with store='$store', priceValue=$priceValue, category='$category', dateAdded='$dateAdded'"
                    )

                    if (store.isNotEmpty() && priceValue != null && category.isNotBlank())
                    {
                        // Create an instance of the API
                        val newExpense = ExpenseEntity(
                            storeName = store,
                            price = priceValue,
                            category = category,
                            date = dateAdded,
                            userID = userID,
                            imageUrl = imageUrl,
                            isSynced = false,
                            documentId = "0"
                        )
                        addExpense(newExpense)
                        Toast.makeText(
                            this,
                            "Expense '${newExpense.storeName}' Added",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else
                    {
                        Log.w(TAG, "showAddExpenseDialog: Incomplete fields for expense")
                        Toast.makeText(this, "Please fill all the fields", Toast.LENGTH_SHORT)
                            .show()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    Log.d(TAG, "showAddExpenseDialog: Dialog cancelled")
                    dialog.dismiss()
                }
                .setNeutralButton("Take Picture") { dialog, _ ->
                    // Neutral button click does not dismiss the dialog
                    Log.d(TAG, "Neutral button clicked: Taking picture...")
                    takePicture() // Call the method to take a picture
                    // Do not dismiss the dialog here
                }
                .show()

            // Set up the button behavior manually to avoid dismissing
            expenseDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                Log.d(TAG, "Neutral button clicked: Taking picture...")
                takePicture() // Call the method to take a picture
                // Do not dismiss the dialog
            }
        }
    }

    private fun fetchCategories(userID: String, callback: (List<String>) -> Unit)
    {
        Log.d(TAG, "Fetching categories for user: $userID")

        // Check if network is available
        if (isNetworkAvailable(this))
        {
            val apiService = RetrofitClient.instance.create(WalletMavenAPIService::class.java)
            apiService.getCategories(userID).enqueue(object : Callback<CategoryResponse>
            {
                override fun onResponse(
                    call: Call<CategoryResponse>,
                    response: Response<CategoryResponse>
                )
                {
                    if (response.isSuccessful)
                    {
                        val categoriesResponse = response.body()
                        val categories = categoriesResponse?.data?.map { it.name } ?: emptyList()
                        Log.d(TAG, "Fetched categories from API: $categories")

                        // Callback with fetched categories
                        callback(categories)
                    } else
                    {
                        Log.e(TAG, "Error fetching categories from API: ${response.message()}")
                        // Fetch categories from Room if API call fails
                        fetchCategoriesFromRoom(userID, callback)
                    }
                }

                override fun onFailure(call: Call<CategoryResponse>, t: Throwable)
                {
                    Log.e(TAG, "Failure fetching categories from API: ${t.message}")
                    // Fetch categories from Room if API call fails
                    fetchCategoriesFromRoom(userID, callback)
                }
            })
        } else
        {
            // No network available, fetch from RoomDB
            Log.d(TAG, "No network available, fetching categories from RoomDB")
            fetchCategoriesFromRoom(userID, callback)
        }
    }

    private fun addExpense(expense: ExpenseEntity)
    {
        CoroutineScope(Dispatchers.IO).launch {
            expenseDao.insertExpense(expense)
            Log.d(TAG, "Expense added to Room DB: ${expense.storeName}")
            // Schedule sync worker to upload data to API
            scheduleSyncWorker()
        }
    }

    private fun addCategory(category: CategoryEntity)
    {
        CoroutineScope(Dispatchers.IO).launch {
            categoryDao.insertCategory(category)
            Log.d(TAG, "Category added to Room DB: ${category.name}")
            // Schedule sync worker to upload data to API
            scheduleSyncWorker()
        }
    }

    private fun fetchCategoriesFromRoom(userID: String, callback: (List<String>) -> Unit)
    {
        Log.d(TAG, "Fetching categories from Room for user: $userID")

        // Fetch categories from RoomDB using the provided userID
        CoroutineScope(Dispatchers.IO).launch {
            val categories =
                categoryDao.getCategoriesByUser(userID).map { it.name } // Get category names
            if (categories.isNotEmpty())
            {
                // Call the callback with the fetched categories
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Fetched categories from Room DB: $categories")
                    callback(categories) // Use the callback to return categories
                }
            } else
            {
                Log.d(TAG, "No categories found in Room DB for user: $userID")
                withContext(Dispatchers.Main) {
                    callback(emptyList()) // Call the callback with an empty list if no categories found
                }
            }
        }
    }

    private fun takePicture()
    {
        // Check for camera permission
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            // Request permission
            requestPermissions(
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else
        {
            // Permission already granted, proceed to take picture
            startCameraIntent()
        }
    }

    // Handle the permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    )
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE)
        {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                // Permission granted, proceed to take picture
                startCameraIntent()
            } else
            {
                // Permission denied
                Toast.makeText(
                    this,
                    "Camera permission is required to take pictures",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Helper method to start the camera intent
    private fun startCameraIntent()
    {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null)
        {
            val photoFile: File? = createImageFile()
            photoFile?.also {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    it
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(intent, IMAGE_REQUEST_CODE)
            }
        }
    }

    private fun createImageFile(): File
    {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",  /* prefix */
            ".jpg",         /* suffix */
            storageDir      /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath // Store the path for later use
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_REQUEST_CODE && resultCode == RESULT_OK)
        {
            // Assuming you are using a valid image URI
            val imageUri = Uri.fromFile(File(currentPhotoPath))
            selectedImageUri = imageUri // Set the selected image URI

            // Check if the dialog is still showing
            if (::expenseDialog.isInitialized && expenseDialog.isShowing)
            {
                // Set the image in the ImageView of the dialog
                dialogBinding.imageView.setImageURI(selectedImageUri)
                Log.d(TAG, "Image set in dialog's ImageView")
            } else
            {
                Log.d(TAG, "Dialog is no longer showing")
            }
        }
        if (requestCode == MyBiometricManager.REQUEST_CODE)
        {
            if (resultCode == RESULT_OK)
            {
                // Biometric authentication successful
                Log.d(TAG, "Biometric authentication successful")
                // Handle successful authentication here (e.g., proceed to secure features)
            } else
            {
                // Authentication failed or was cancelled
                Log.d(TAG, "Biometric authentication failed or canceled")
                logoutUser()
            }
        }
    }

    // Function to get the current date in ISO 8601 format
    private fun getCurrentISODate(): String
    {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC") // Make sure it's in UTC
        return dateFormat.format(Date()) // Current date
    }

    private fun setLocale(languageCode: String)
    {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = resources.configuration
        config.setLocale(locale)

        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean
    {
        menuInflater.inflate(R.menu.overflow_menu, menu)
        updateMenuItems(menu) // Call to update menu item titles based on the current language
        return true
    }

    private fun updateMenuItems(menu: Menu?)
    {
        val languageCode = preferences.getString("selected_language", "en") ?: "en"
        menu?.findItem(R.id.action_open_settings)?.title = when (languageCode)
        {
            "en" -> "Settings"
            "af" -> "Instellings"
            else -> "Settings" // Fallback to English
        }
        menu?.findItem(R.id.action_logout)?.title = when (languageCode)
        {
            "en" -> "Logout"
            "af" -> "Teken Uit"
            else -> "Logout" // Fallback to English
        }
    }

    override fun onResume()
    {
        super.onResume()
        invalidateOptionsMenu() // Refresh the menu on resume
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        return when (item.itemId)
        {
            android.R.id.home ->
            {
                finish()
                true
            }

            R.id.action_open_settings ->
            {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }

            R.id.action_logout -> {
                logoutUser()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBiometricAuthenticationResult(result: String?, errString: CharSequence?)
    {
        when (result)
        {
            MyBiometricManager.Callback.AUTHENTICATION_SUCCESSFUL ->
            {
                // Handle successful authentication
                Log.d(TAG, "Authentication successful")
                failedAttempts = 0 // Reset counter on success
            }

            MyBiometricManager.Callback.AUTHENTICATION_FAILED ->
            {
                // Handle failed authentication
                Log.d(TAG, "Authentication failed")
                failedAttempts++

                if (failedAttempts >= maxAttempts)
                {
                    Log.d(TAG, "Maximum attempts reached. Logging out.")
                    logoutUser() // Call your logout function
                }
            }

            MyBiometricManager.Callback.AUTHENTICATION_ERROR ->
            {
                // Handle authentication error
                Log.e(TAG, "Authentication error: $errString")
                logoutUser()
            }
        }
    }

    private fun logoutUser()
    {
        auth.signOut()
        // Redirect to the login activity
        val intent = Intent(this, Login::class.java)
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear the back stack
        startActivity(intent)
        finish()
    }

    private fun isNetworkAvailable(context: Context): Boolean
    {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            return when
            {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else
        {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    private fun scheduleSyncWorker()
    {
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "SyncWorker", // Unique name for this worker
                ExistingWorkPolicy.KEEP, // Keep the existing work if it's already running
                syncWorkRequest
            )
    }

    companion object
    {
        private const val IMAGE_REQUEST_CODE = 1001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1002
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1003
    }
}
