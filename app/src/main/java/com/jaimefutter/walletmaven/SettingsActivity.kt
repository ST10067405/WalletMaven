package com.jaimefutter.walletmaven

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var darkModeSwitch: SwitchMaterial
    private val TAG = "SettingsActivity" // Log tag for this activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SharedPreferences first
        preferences = getSharedPreferences("app_preferences", MODE_PRIVATE)

        // Load saved language preference before setting the content view
        setLanguage()

        setContentView(R.layout.activity_settings)

        // Setup View Binding
        darkModeSwitch = findViewById(R.id.switch_dark_mode)

        // Load the saved theme preference
        val isDarkTheme: Boolean = if (preferences.contains("dark_mode")) {
            preferences.getBoolean("dark_mode", false).also {
                Log.d(TAG, "Loaded dark mode preference: $it")
            }
        } else {
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Set the switch state based on the determined preference
        darkModeSwitch.isChecked = isDarkTheme
        Log.d(TAG, "Dark mode switch initialized: ${darkModeSwitch.isChecked}")

        // Update the status bar color based on the theme
        updateStatusBarColor(isDarkTheme)

        // Handle dark mode switch toggle
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Save the preference
            preferences.edit().putBoolean("dark_mode", isChecked).apply()
            Log.d(TAG, "Dark mode preference saved: $isChecked")
            // Toggle the app theme
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            // Update the status bar color
            updateStatusBarColor(isChecked)
        }

        // Setup currency selection functionality
        setupCurrencySelection()

        // Setup language selection functionality
        setupLanguageSelection()

        // Enable the up button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Settings"
    }

    private fun setLanguage() {
        val currentLanguageCode = preferences.getString("selected_language", "en") ?: "en"
        val locale = Locale(currentLanguageCode)
        Locale.setDefault(locale)

        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Update action bar title based on the current language
        updateActionBarTitle(currentLanguageCode)

        Log.d(TAG, "Locale set to: $currentLanguageCode")
    }

    private fun updateActionBarTitle(languageCode: String) {
        val title = when (languageCode) {
            "en" -> "Settings"
            "af" -> "Instellings"
            else -> "Settings" // Fallback to English if language is unrecognized
        }
        supportActionBar?.title = title
    }

    private fun setupLanguageSelection()
    {
        // Initialize language data
        val languageNames = listOf("English", "Afrikaans")
        val languageCodes = listOf("en", "af")

        // Find the AutoCompleteTextView for language selection
        val languageSelectText: MaterialAutoCompleteTextView =
            findViewById(R.id.text_select_language)

        // Set up the adapter for the dropdown
        val languageAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, languageNames)
        languageSelectText.setAdapter(languageAdapter)

        // Load the saved language preference
        val currentLanguageCode = preferences.getString("selected_language", "en") ?: "en"
        val currentLanguageIndex = languageCodes.indexOf(currentLanguageCode)

        // Pre-select the saved language if found
        if (currentLanguageIndex != -1)
        {
            languageSelectText.setText(languageNames[currentLanguageIndex], false)
        }

        // Handle language selection changes
        languageSelectText.setOnItemClickListener { parent, view, position, id ->
            val selectedLanguageCode = languageCodes[position]
            saveLanguagePreference(selectedLanguageCode)
            setLocale(selectedLanguageCode)
            Toast.makeText(
                this,
                "Language changed to ${languageNames[position]}",
                Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "Language changed to: ${languageNames[position]}")

            // Notify MainActivity to refresh its menu
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish() // Close SettingsActivity

            // Recreate the current activity to apply the language change
            recreate()
        }
    }

    private fun saveLanguagePreference(languageCode: String) {
        preferences.edit().putString("selected_language", languageCode).apply()
        Log.d(TAG, "Saved language preference: $languageCode")
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = resources.configuration
        config.setLocale(locale)

        resources.updateConfiguration(config, resources.displayMetrics)
        Log.d(TAG, "Locale set to: $languageCode")
    }

    private fun setupCurrencySelection() {
        // Initialize currency data
        val currencyNames = listOf("South African Rand (R)", "US Dollar ($)", "British Pound (£)")
        val currencyFormats = listOf("R", "$", "£")

        // Find the AutoCompleteTextView for currency selection
        val currencyFormatText: MaterialAutoCompleteTextView = findViewById(R.id.text_currency_format)

        // Set up the adapter for the dropdown
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, currencyNames)
        currencyFormatText.setAdapter(currencyAdapter)

        // Load the saved currency format preference
        val savedCurrencySymbol = preferences.getString("currency_symbol", "R") ?: "R"
        val savedCurrencyIndex = currencyFormats.indexOf(savedCurrencySymbol)

        // Pre-select the saved currency if found
        if (savedCurrencyIndex != -1) {
            currencyFormatText.setText(currencyNames[savedCurrencyIndex], false)
        }

        // Handle currency selection changes
        currencyFormatText.setOnItemClickListener { parent, view, position, id ->
            val selectedCurrencySymbol = currencyFormats[position]
            saveCurrencyPreference(selectedCurrencySymbol)
            Toast.makeText(this, "Currency format changed to ${currencyNames[position]}", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Currency format changed to: ${currencyNames[position]}")
        }
    }

    private fun saveCurrencyPreference(symbol: String) {
        preferences.edit().putString("currency_symbol", symbol).apply()
        Log.d(TAG, "Saved currency preference: $symbol")
    }

    private fun updateStatusBarColor(isDarkTheme: Boolean) {
        if (isDarkTheme) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.primaryColorDark)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.primaryColorDark)
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.statusNav)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.statusNav)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowInsetsController = window.insetsController
                windowInsetsController?.setSystemBarsAppearance(
                    0, // No light status bar
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                window.decorView.systemUiVisibility = 0 // No light status bar
            }
        }
        Log.d(TAG, "Status bar color updated for dark theme: $isDarkTheme")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish() // Handle back button
                Log.d(TAG, "Back button pressed.")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
