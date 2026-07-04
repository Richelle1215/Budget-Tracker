package com.example.budgettracker.viewmodel

import android.content.ContentResolver
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.budgettracker.data.Transaction
import com.example.budgettracker.data.TransactionType

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

    fun exportToCsv(contentResolver: ContentResolver, uri: Uri) {
        val csv = StringBuilder("Name,Amount,Date,Type\n")
        transactions.forEach {
            csv.append("${it.name},${it.amount},${it.date},${it.type}\n")
        }
        contentResolver.openOutputStream(uri)?.use {
            it.write(csv.toString().toByteArray())
        }
    }

    fun exportToPdf(contentResolver: ContentResolver, uri: Uri) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(300, 600, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()
        
        var y = 40f
        canvas.drawText("Budget Report", 100f, y, paint)
        y += 20f
        canvas.drawText("Initial Balance: ${initialBalance.value}", 20f, y, paint)
        y += 20f
        canvas.drawText("Total Income: $totalIncome", 20f, y, paint)
        y += 20f
        canvas.drawText("Total Expenses: $totalExpenses", 20f, y, paint)
        y += 20f
        canvas.drawText("Current Balance: $currentBalance", 20f, y, paint)
        y += 30f
        
        canvas.drawText("Transactions:", 20f, y, paint)
        y += 20f
        
        transactions.forEach {
            if (y > 580) { // Very basic pagination check
                pdfDocument.finishPage(page)
                // In a real app we'd create a new page here
                return@forEach 
            }
            val text = "${it.date} - ${it.name}: ${if (it.type == TransactionType.INCOME) "+" else "-"}${it.amount}"
            canvas.drawText(text, 20f, y, paint)
            y += 15f
        }

        pdfDocument.finishPage(page)
        contentResolver.openOutputStream(uri)?.use {
            pdfDocument.writeTo(it)
        }
        pdfDocument.close()
    }
}
