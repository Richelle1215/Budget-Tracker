package com.example.budgettracker

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.budgettracker.data.Transaction
import com.example.budgettracker.data.TransactionType
import com.example.budgettracker.ui.theme.*
import com.example.budgettracker.viewmodel.BudgetViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: BudgetViewModel
    private lateinit var createCsvLauncher: ActivityResultLauncher<String>
    private lateinit var createPdfLauncher: ActivityResultLauncher<String>
    private var filteredListToExport: List<Transaction> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        viewModel = ViewModelProvider(this)[BudgetViewModel::class.java]
        
        createCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { viewModel.exportToCsv(contentResolver, it, filteredListToExport) }
        }
        
        createPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            uri?.let { viewModel.exportToPdf(contentResolver, it, filteredListToExport) }
        }

        setContent {
            BudgetTrackerTheme {
                var showOnboarding by remember { mutableStateOf(viewModel.isNewDay()) }
                var showExportExcelDialog by remember { mutableStateOf(false) }
                var showExportPdfDialog by remember { mutableStateOf(false) }

                if (showOnboarding) {
                    OnboardingScreen { balance ->
                        viewModel.setInitialBalance(balance)
                        showOnboarding = false
                    }
                } else {
                    BudgetApp(
                        viewModel = viewModel,
                        onExportCsv = { showExportExcelDialog = true },
                        onExportPdf = { showExportPdfDialog = true }
                    )
                }

                if (showExportExcelDialog) {
                    ExportDialog(
                        title = "Export as Excel",
                        icon = "📊",
                        buttonText = "Download Excel",
                        buttonColor = MaterialTheme.colorScheme.primary,
                        onDismiss = { showExportExcelDialog = false },
                        onDownload = { start, end ->
                            filteredListToExport = viewModel.getFilteredTransactions(start, end)
                            createCsvLauncher.launch("budget_tracker_${System.currentTimeMillis()}.csv")
                            showExportExcelDialog = false
                        }
                    )
                }

                if (showExportPdfDialog) {
                    ExportDialog(
                        title = "Export as PDF",
                        icon = "📄",
                        buttonText = "Download PDF",
                        buttonColor = Color(0xFFB13B2E),
                        onDismiss = { showExportPdfDialog = false },
                        onDownload = { start, end ->
                            filteredListToExport = viewModel.getFilteredTransactions(start, end)
                            createPdfLauncher.launch("budget_tracker_${System.currentTimeMillis()}.pdf")
                            showExportPdfDialog = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(
    title: String,
    icon: String,
    buttonText: String,
    buttonColor: Color,
    onDismiss: () -> Unit,
    onDownload: (String, String) -> Unit
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()
    
    var startDate by remember { mutableStateOf(sdf.format(Date())) }
    var endDate by remember { mutableStateOf(sdf.format(Date())) }

    fun showDatePicker(onDateSelected: (String) -> Unit) {
        DatePickerDialog(context, { _, year, month, day ->
            val selected = Calendar.getInstance()
            selected.set(year, month, day)
            onDateSelected(sdf.format(selected.time))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(
                            "Select the date range to include in the report.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(icon, fontSize = 32.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        InputLabel("START DATE *")
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            modifier = Modifier.fillMaxWidth().clickable { showDatePicker { startDate = it } },
                            colors = customTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = false,
                            readOnly = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.Black)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        InputLabel("END DATE *")
                        OutlinedTextField(
                            value = endDate,
                            onValueChange = { endDate = it },
                            modifier = Modifier.fillMaxWidth().clickable { showDatePicker { endDate = it } },
                            colors = customTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = false,
                            readOnly = true,
                            textStyle = LocalTextStyle.current.copy(color = Color.Black)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val filterButtons = listOf("This Month", "Last Month", "This Year", "All Time")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterButtons.forEach { label ->
                        Surface(
                            onClick = { 
                                val cal = Calendar.getInstance()
                                when (label) {
                                    "This Month" -> {
                                        cal.set(Calendar.DAY_OF_MONTH, 1)
                                        startDate = sdf.format(cal.time)
                                        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                                        endDate = sdf.format(cal.time)
                                    }
                                    "Last Month" -> {
                                        cal.add(Calendar.MONTH, -1)
                                        cal.set(Calendar.DAY_OF_MONTH, 1)
                                        startDate = sdf.format(cal.time)
                                        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                                        endDate = sdf.format(cal.time)
                                    }
                                    "This Year" -> {
                                        cal.set(Calendar.DAY_OF_YEAR, 1)
                                        startDate = sdf.format(cal.time)
                                        cal.set(Calendar.DAY_OF_YEAR, cal.getActualMaximum(Calendar.DAY_OF_YEAR))
                                        endDate = sdf.format(cal.time)
                                    }
                                    "All Time" -> {
                                        startDate = "2000-01-01"
                                        endDate = sdf.format(Date())
                                    }
                                }
                            },
                            color = Color(0xFFF5EFE7),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onDownload(startDate, endDate) },
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(buttonText, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    )
}

@Composable
fun OnboardingScreen(onGetStarted: (Double) -> Unit) {
    var balanceInput by remember { mutableStateOf("") }
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF4A7C59), Color(0xFF7D4427))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💰", fontSize = 48.sp)
                    Text(
                        "Budget Tracker",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Track your income & expenses with ease",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Card(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Set Starting Balance",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        "Enter how much money you currently have.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        "STARTING BALANCE (₱) *",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    OutlinedTextField(
                        value = balanceInput,
                        onValueChange = { balanceInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        prefix = { Text("₱ ") },
                        placeholder = { Text("0.00") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.secondary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.secondary,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { onGetStarted(balanceInput.toDoubleOrNull() ?: 0.0) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Get Started", fontWeight = FontWeight.Bold, color = Color.White)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp), tint = Color.White)
                        }
                    }
                    
                    Text(
                        "Start with ₱0.00 balance",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                BudgetHeader(
                    currentBalance = viewModel.currentBalance,
                    onExportCsv = onExportCsv,
                    onExportPdf = onExportPdf
                )
            }
            
            item {
                SummaryCardsRow(
                    startingBalance = viewModel.initialBalance.value,
                    totalIncome = viewModel.totalIncome,
                    totalExpenses = viewModel.totalExpenses,
                    remainingBalance = viewModel.currentBalance,
                    incomeCount = viewModel.transactions.count { it.type == TransactionType.INCOME },
                    expenseCount = viewModel.transactions.count { it.type == TransactionType.EXPENSE }
                )
            }
            
            item {
                ExpensesVsIncomeSection(
                    income = viewModel.totalIncome,
                    expenses = viewModel.totalExpenses
                )
            }
            
            item {
                TransactionForm(onAddTransaction = { name, amount, date, type, note ->
                    viewModel.addTransaction(name, amount, date, type, note)
                })
            }
            
            item {
                Text(
                    "Recent Transactions",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            }
            
            items(viewModel.transactions.reversed()) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    onDelete = { viewModel.deleteTransaction(transaction) }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun BudgetHeader(
    currentBalance: Double,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF4A7C59), Color(0xFF7D4427))
                )
            )
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💰", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Budget Tracker",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Personal Finance Manager",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
                
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.Green, RoundedCornerShape(4.dp))
                        )
                        Text(
                            " ₱${String.format(Locale.getDefault(), "%.2f", currentBalance)} ",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text("-", color = Color.White)
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExportCsv,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📊 Export Excel", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = onExportPdf,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📄 Export PDF", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SummaryCardsRow(
    startingBalance: Double,
    totalIncome: Double,
    totalExpenses: Double,
    remainingBalance: Double,
    incomeCount: Int,
    expenseCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCard(
            label = "STARTING BALANCE",
            value = startingBalance,
            subLabel = "Initial amount",
            color = Color(0xFF7D4427),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "TOTAL INCOME",
            value = totalIncome,
            subLabel = "$incomeCount entries",
            color = Color(0xFF4A7C59),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "TOTAL EXPENSES",
            value = totalExpenses,
            subLabel = "$expenseCount entries",
            color = Color(0xFFB13B2E),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "REMAINING BALANCE",
            value = remainingBalance,
            subLabel = "Available",
            color = Color(0xFF2E5B31),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SummaryCard(
    label: String,
    value: Double,
    subLabel: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(110.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "₱${String.format(Locale.getDefault(), "%.2f", value)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(subLabel, fontSize = 8.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ExpensesVsIncomeSection(income: Double, expenses: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Expenses vs Income", fontWeight = FontWeight.Bold, color = Color.Black)
                val percentSpent = if (income > 0) (expenses / income * 100).toInt() else 0
                Text("$percentSpent% spent", fontSize = 12.sp, color = Color.Gray)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { if (income > 0) (expenses / income).toFloat().coerceIn(0f, 1f) else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("+₱${String.format(Locale.getDefault(), "%.2f", income)} income", color = IncomeGreen, fontSize = 12.sp)
                Text("-₱${String.format(Locale.getDefault(), "%.2f", expenses)} expenses", color = ExpenseRed, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun TransactionForm(onAddTransaction: (String, Double, String, TransactionType, String) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Expense, 1: Income
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF7F2)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                TabButton(
                    text = "Expense",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                    selectedColor = Color(0xFFB13B2E)
                )
                TabButton(
                    text = "Income",
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                    selectedColor = Color(0xFF4A7C59)
                )
            }
            
            TransactionInputs(
                type = if (selectedTab == 0) TransactionType.EXPENSE else TransactionType.INCOME,
                onAdd = onAddTransaction
            )
        }
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier, selectedColor: Color) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (isSelected) Color.White else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isSelected) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = if (isSelected) selectedColor else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text,
                    color = if (isSelected) selectedColor else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(40.dp)
                        .height(2.dp)
                        .background(selectedColor)
                )
            }
        }
    }
}

@Composable
fun TransactionInputs(type: TransactionType, onAdd: (String, Double, String, TransactionType, String) -> Unit) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()
    
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(sdf.format(Date())) }
    var note by remember { mutableStateOf("") }
    
    fun showDatePicker() {
        DatePickerDialog(context, { _, year, month, day ->
            val selected = Calendar.getInstance()
            selected.set(year, month, day)
            date = sdf.format(selected.time)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }
    
    val accentColor = if (type == TransactionType.EXPENSE) Color(0xFFB13B2E) else Color(0xFF4A7C59)
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Add ${if (type == TransactionType.EXPENSE) "Expense" else "Income"}", fontWeight = FontWeight.Bold, color = Color.Black)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InputLabel("DESCRIPTION *")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("e.g. Groceries, Electric bill...") },
            modifier = Modifier.fillMaxWidth(),
            colors = customTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            textStyle = LocalTextStyle.current.copy(color = Color.Black)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                InputLabel("AMOUNT (₱) *")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    prefix = { Text("₱ ") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = LocalTextStyle.current.copy(color = Color.Black)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                InputLabel("DATE")
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker() },
                    colors = customTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = false,
                    readOnly = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.Black)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        InputLabel("NOTE (OPTIONAL)")
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text("Additional details...") },
            modifier = Modifier.fillMaxWidth(),
            colors = customTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            textStyle = LocalTextStyle.current.copy(color = Color.Black)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (name.isNotBlank() && amt > 0) {
                    onAdd(name, amt, date, type, note)
                    name = ""
                    amount = ""
                    note = ""
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add ${if (type == TransactionType.EXPENSE) "Expense" else "Income"}", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun InputLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF7D4427),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xFFF5EFE7),
    unfocusedContainerColor = Color(0xFFF5EFE7),
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledContainerColor = Color(0xFFF5EFE7),
    disabledBorderColor = Color.Transparent,
    disabledTextColor = Color.Black
)

@Composable
fun TransactionItem(transaction: com.example.budgettracker.data.Transaction, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (transaction.type == TransactionType.INCOME) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (transaction.type == TransactionType.INCOME) "↓" else "↑", 
                        color = if (transaction.type == TransactionType.INCOME) IncomeGreen else ExpenseRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(transaction.name, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("${transaction.date} • ${transaction.time}", fontSize = 12.sp, color = Color.Gray)
                    if (transaction.note.isNotBlank()) {
                        Text(transaction.note, fontSize = 11.sp, color = Color.DarkGray)
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (transaction.type == TransactionType.INCOME) "+" else "-"}₱${String.format(Locale.getDefault(), "%.2f", transaction.amount)}",
                    color = if (transaction.type == TransactionType.INCOME) IncomeGreen else ExpenseRed,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
