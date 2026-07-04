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
    private val stf = SimpleDateFormat("hh:mm a", Locale.getDefault())

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

    fun addTransaction(name: String, amount: Double, date: String, type: TransactionType, note: String = "") {
        val currentTime = stf.format(Date())
        transactions.add(Transaction(name = name, amount = amount, date = date, time = currentTime, type = type, note = note))
        saveData()
    }

    fun deleteTransaction(transaction: Transaction) {
        transactions.remove(transaction)
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

    fun exportToCsv(contentResolver: ContentResolver, uri: Uri, filteredList: List<Transaction>) {
        val locale = Locale.getDefault()
        val filterTotalIncome = filteredList.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val filterTotalExpenses = filteredList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val net = filterTotalIncome - filterTotalExpenses

        val csv = StringBuilder()
        csv.append("\n")
        csv.append("\n")
        csv.append("\n")
        csv.append("\n")
        csv.append("SUMMARY\n")
        csv.append("Starting Balance,${String.format(locale, "%.2f", initialBalance.value)}\n")
        csv.append("Total Income (in range),${String.format(locale, "%.2f", filterTotalIncome)}\n")
        csv.append("Total Expenses (in range),${String.format(locale, "%.2f", filterTotalExpenses)}\n")
        csv.append("Net (Income - Expense),${String.format(locale, "%.2f", net)}\n")
        csv.append("\n")
        csv.append("TRANSACTIONS\n")
        csv.append("Date,Type,Description,Amount (P),Note\n")
        
        filteredList.forEach {
            val displayAmount = if (it.type == TransactionType.EXPENSE) -it.amount else it.amount
            csv.append("${it.date},${it.type.name},${it.name},${String.format(locale, "%.2f", displayAmount)},${it.note}\n")
        }
        
        csv.append("\n")
        csv.append(",,Total Income,${String.format(locale, "%.2f", filterTotalIncome)}\n")
        csv.append(",,Total Expenses,${String.format(locale, "%.2f", -filterTotalExpenses)}\n")
        csv.append(",,Net,${String.format(locale, "%.2f", net)}\n")

        contentResolver.openOutputStream(uri)?.use {
            it.write(csv.toString().toByteArray())
        }
    }

    fun exportToPdf(contentResolver: ContentResolver, uri: Uri, filteredList: List<Transaction>) {
        val locale = Locale.getDefault()
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()
        
        var y = 50f
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Budget Report", 200f, y, paint)
        
        val filterTotalIncome = filteredList.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val filterTotalExpenses = filteredList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val net = filterTotalIncome - filterTotalExpenses

        y += 40f
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("SUMMARY", 50f, y, paint)
        y += 20f
        canvas.drawText("Starting Balance: P${String.format(locale, "%.2f", initialBalance.value)}", 50f, y, paint)
        y += 20f
        canvas.drawText("Total Income (in range): P${String.format(locale, "%.2f", filterTotalIncome)}", 50f, y, paint)
        y += 20f
        canvas.drawText("Total Expenses (in range): P${String.format(locale, "%.2f", filterTotalExpenses)}", 50f, y, paint)
        y += 20f
        paint.isFakeBoldText = true
        canvas.drawText("Net (Income - Expense): P${String.format(locale, "%.2f", net)}", 50f, y, paint)
        
        y += 40f
        paint.textSize = 16f
        canvas.drawText("TRANSACTIONS", 50f, y, paint)
        y += 30f
        
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("Date", 50f, y, paint)
        canvas.drawText("Type", 120f, y, paint)
        canvas.drawText("Description", 200f, y, paint)
        canvas.drawText("Amount (P)", 400f, y, paint)
        canvas.drawText("Note", 480f, y, paint)
        y += 20f
        paint.isFakeBoldText = false
        
        filteredList.forEach {
            if (y > 800) return@forEach 
            val displayAmount = if (it.type == TransactionType.EXPENSE) -it.amount else it.amount
            canvas.drawText(it.date, 50f, y, paint)
            canvas.drawText(it.type.name, 120f, y, paint)
            canvas.drawText(it.name, 200f, y, paint)
            canvas.drawText(String.format(locale, "%.2f", displayAmount), 400f, y, paint)
            canvas.drawText(it.note, 480f, y, paint)
            y += 20f
        }
        
        if (y < 750) {
            y += 20f
            paint.isFakeBoldText = true
            canvas.drawText("Total Income", 300f, y, paint)
            canvas.drawText(String.format(locale, "%.2f", filterTotalIncome), 400f, y, paint)
            y += 20f
            canvas.drawText("Total Expenses", 300f, y, paint)
            canvas.drawText(String.format(locale, "%.2f", -filterTotalExpenses), 400f, y, paint)
            y += 20f
            canvas.drawText("Net", 300f, y, paint)
            canvas.drawText(String.format(locale, "%.2f", net), 400f, y, paint)
        }

        pdfDocument.finishPage(page)
        contentResolver.openOutputStream(uri)?.use {
            pdfDocument.writeTo(it)
        }
        pdfDocument.close()
    }
}
