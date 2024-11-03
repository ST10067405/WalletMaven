package com.jaimefutter.walletmaven.ui.dashboard

import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.NumberFormat
import java.util.Locale

class CurrencyValueFormatter(private val currencySymbol: String) : ValueFormatter() {
    override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
        // Format the value as a number with two decimal places
        val formattedValue = String.format("%.2f", value) // Format the value to 2 decimal places
        return "$currencySymbol $formattedValue" // Append your currency symbol
    }
}
