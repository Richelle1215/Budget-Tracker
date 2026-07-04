package com.example.budgettracker.viewmodel

import android.content.ContentResolver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.budgettracker.data.Transaction
import com.example.budgettracker.data.TransactionType
import java.text.SimpleDateFormat
import java.util.*

class BudgetViewModel : ViewModel() {
    var initialBalance = mutableStateOf(0.0)
    val transactions = mutableStateListOf<Transaction>()

    val totalIncome: Double
        get() = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

    val totalExpenses: Double
        get() = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    val currentBalance: Double
        get() = initialBalance.value + totalIncome - totalExpenses

    fun addTransaction(name: String, amount: Double, date: String, type: TransactionType) {
        transactions.add(Transaction(name = name, amount = amount, date = date, type = type))
    }

    fun setInitialBalance(balance: Double) {
        initialBalance.value = balance
    }

    fun getFilteredTransactions(startDateStr: String, endDateStr: String): List<Transaction> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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
        canvas.drawText("Initial Balance: ₱${String.format("%.2f", initialBalance.value)}", 50f, y, paint)
        y += 25f
        canvas.drawText("Total Income: ₱${String.format("%.2f", totalIncome)}", 50f, y, paint)
        y += 25f
        canvas.drawText("Total Expenses: ₱${String.format("%.2f", totalExpenses)}", 50f, y, paint)
        y += 25f
        paint.isFakeBoldText = true
        canvas.drawText("Current Balance: ₱${String.format("%.2f", currentBalance)}", 50f, y, paint)
        
        y += 40f
        paint.textSize = 16f
        canvas.drawText("Transactions:", 50f, y, paint)
        y += 30f
        
        paint.textSize = 12f
        paint.isFakeBoldText = false
        
        // Draw Table Header
        paint.isFakeBoldText = true
        canvas.drawText("Date", 50f, y, paint)
        canvas.drawText("Name", 150f, y, paint)
        canvas.drawText("Type", 350f, y, paint)
        canvas.drawText("Amount", 450f, y, paint)
        y += 20f
        paint.isFakeBoldText = false
        
        filteredList.forEach {
            if (y > 800) { 
                // In a production app, we would handle multiple pages here
                return@forEach 
            }
            canvas.drawText(it.date, 50f, y, paint)
            canvas.drawText(it.name, 150f, y, paint)
            canvas.drawText(it.type.name, 350f, y, paint)
            canvas.drawText("₱${String.format("%.2f", it.amount)}", 450f, y, paint)
            y += 20f
        }

        pdfDocument.finishPage(page)
        contentResolver.openOutputStream(uri)?.use {
            pdfDocument.writeTo(it)
        }
        pdfDocument.close()
    }
}
