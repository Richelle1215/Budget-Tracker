package com.example.budgettracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider

import com.example.budgettracker.data.TransactionType
import com.example.budgettracker.ui.theme.BudgetTrackerTheme
import com.example.budgettracker.viewmodel.BudgetViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: BudgetViewModel
    private lateinit var createCsvLauncher: ActivityResultLauncher<String>
    private lateinit var createPdfLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        viewModel = ViewModelProvider(this)[BudgetViewModel::class.java]
        
        createCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { viewModel.exportToCsv(contentResolver, it) }
        }
        
        createPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            uri?.let { viewModel.exportToPdf(contentResolver, it) }
        }

        setContent {
            BudgetTrackerTheme {
                BudgetApp(
                    viewModel = viewModel,
                    onExportCsv = { createCsvLauncher.launch("budget_tracker_${System.currentTimeMillis()}.csv") },
                    onExportPdf = { createPdfLauncher.launch("budget_tracker_${System.currentTimeMillis()}.pdf") }
                )
            }
        }
    }
}

@Composable
fun BudgetApp(
    viewModel: BudgetViewModel,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { 
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        mutableStateOf(sdf.format(Date())) 
    }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var initialBalanceInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(modifier = Modifier.padding(16.dp).statusBarsPadding()) {
                Text("Budget Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Initial Balance Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = initialBalanceInput,
                    onValueChange = { initialBalanceInput = it },
                    label = { Text("Initial Balance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val bal = initialBalanceInput.toDoubleOrNull() ?: 0.0
                        viewModel.setInitialBalance(bal)
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Set")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Summary", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Income:")
                        Text("₱${viewModel.totalIncome}", color = Color(0xFF4CAF50))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Expenses:")
                        Text("₱${viewModel.totalExpenses}", color = Color.Red)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Balance:", fontWeight = FontWeight.Bold)
                        Text("₱${viewModel.currentBalance}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction Form
            Text("Add Transaction", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name/Description") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME })
                Text("Income")
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE })
                Text("Expense")
            }
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && amt > 0) {
                        viewModel.addTransaction(name, amt, date, type)
                        name = ""
                        amount = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Transaction")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction List
            Text("Transactions", fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(viewModel.transactions.reversed()) { transaction ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(transaction.name, fontWeight = FontWeight.Bold)
                                Text(transaction.date, fontSize = 12.sp)
                            }
                            Text(
                                text = "${if (transaction.type == TransactionType.INCOME) "+" else "-"}₱${transaction.amount}",
                                color = if (transaction.type == TransactionType.INCOME) Color(0xFF4CAF50) else Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Export Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = onExportCsv) {
                    Text("Excel (CSV)")
                }
                Button(onClick = onExportPdf) {
                    Text("PDF")
                }
            }
        }
    }
}
