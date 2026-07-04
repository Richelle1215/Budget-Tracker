package com.example.budgettracker.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.example.budgettracker.data.Transaction
import com.example.budgettracker.data.TransactionType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    var initialBalance = mutableStateOf(0.0)
    val transactions = mutableStateListOf<Transaction>()
    var lastResetDate = mutableStateOf("")

    val totalIncome: Double
        get() = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

    val totalExpenses: Double
        get() = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    val currentBalance: Double
        get() = initialBalance.value + totalIncome - totalExpenses

    init {
        loadData()
    }

    private fun loadData() {
        initialBalance.value = prefs.getFloat("initial_balance", 0.0f).toDouble()
        lastResetDate.value = prefs.getString("last_reset_date", "") ?: ""
        
        val transactionsJson = prefs.getString("transactions", null)
        if (transactionsJson != null) {
            val type = object : TypeToken<List<Transaction>>() {}.type
            val list: List<Transaction> = gson.fromJson(transactionsJson, type)
            transactions.clear()
            transactions.addAll(list)
        }
    }

    private fun saveData() {
        prefs.edit().apply {
            putFloat("initial_balance", initialBalance.value.toFloat())
            putString("last_reset_date", lastResetDate.value)
            putString("transactions", gson.toJson(transactions.toList()))
            apply()
        }
    }

    fun isNewDay(): Boolean {
        val today = sdf.format(Date())
        return lastResetDate.value != today
    }

    fun addTransaction(name: String, amount: Double, date: String, type: TransactionType) {
        transactions.add(Transaction(name = name, amount = amount, date = date, type = type))
        saveData()
    }

    fun setInitialBalance(balance: Double) {
        initialBalance.value = balance
        lastResetDate.value = sdf.format(Date())
        saveData()
    }

    fun getFilteredTransactions(startDateStr: String, endDateStr: String): List<Transaction> {
        return try {
            val startDate = sdf.parse(startDateStr)
            val endDate = sdf.parse(endDateStr)
            transactions.filter {
                val tDate = sdf.parse(it.date)
                tDate != null && !tDate.before(startDate) && !tDate.after(endDate)
            }
        } catch (e: Exception) {
            transactions
        }
    }

    fun exportToCsv(contentResolver: ContentResolver, uri: Uri, filteredList: List<Transaction> = transactions) {
        val csv = StringBuilder("Name,Amount,Date,Type\n")
        filteredList.forEach {
            csv.append("${it.name},${it.amount},${it.date},${it.type}\n")
        }
        contentResolver.openOutputStream(uri)?.use {
            it.write(csv.toString().toByteArray())
        }
    }

    fun exportToPdf(contentResolver: ContentResolver, uri: Uri, filteredList: List<Transaction> = transactions) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()
        
        var y = 50f
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Budget Report", 200f, y, paint)
        
        y += 40f
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("Initial Balance: ₱${String.format(Locale.getDefault(), "%.2f", initialBalance.value)}", 50f, y, paint)
        y += 25f
        canvas.drawText("Total Income: ₱${String.format(Locale.getDefault(), "%.2f", totalIncome)}", 50f, y, paint)
        y += 25f
        canvas.drawText("Total Expenses: ₱${String.format(Locale.getDefault(), "%.2f", totalExpenses)}", 50f, y, paint)
        y += 25f
        paint.isFakeBoldText = true
        canvas.drawText("Current Balance: ₱${String.format(Locale.getDefault(), "%.2f", currentBalance)}", 50f, y, paint)
        
        y += 40f
        paint.textSize = 16f
        canvas.drawText("Transactions:", 50f, y, paint)
        y += 30f
        
        paint.textSize = 12f
        paint.isFakeBoldText = false
        
        paint.isFakeBoldText = true
        canvas.drawText("Date", 50f, y, paint)
        canvas.drawText("Name", 150f, y, paint)
        canvas.drawText("Type", 350f, y, paint)
        canvas.drawText("Amount", 450f, y, paint)
        y += 20f
        paint.isFakeBoldText = false
        
        filteredList.forEach {
            if (y > 800) { 
                return@forEach 
            }
            canvas.drawText(it.date, 50f, y, paint)
            canvas.drawText(it.name, 150f, y, paint)
            canvas.drawText(it.type.name, 350f, y, paint)
            canvas.drawText("₱${String.format(Locale.getDefault(), "%.2f", it.amount)}", 450f, y, paint)
            y += 20f
        }

        pdfDocument.finishPage(page)
        contentResolver.openOutputStream(uri)?.use {
            pdfDocument.writeTo(it)
        }
        pdfDocument.close()
    }
}
