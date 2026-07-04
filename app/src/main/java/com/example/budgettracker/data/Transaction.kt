package com.example.budgettracker.data

enum class TransactionType {
    INCOME, EXPENSE
}

data class Transaction(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val amount: Double,
    val date: String,
    val time: String,
    val type: TransactionType,
    val note: String = ""
)
