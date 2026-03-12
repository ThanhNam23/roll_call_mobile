package com.example.roll_call.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.AttendanceRecord
import com.example.roll_call.domain.model.AttendanceStatus
import com.example.roll_call.ui.viewmodel.SummaryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceSummaryScreen(
    sessionId: String,
    onDone: () -> Unit,
    viewModel: SummaryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSummary(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kết quả điểm danh") }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                ) {
                    Text("Về trang chủ", fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Summary Card
                        uiState.session?.let { session ->
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            session.className,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val dateStr = SimpleDateFormat(
                                            "dd/MM/yyyy HH:mm",
                                            Locale.getDefault()
                                        ).format(session.date.toDate())
                                        Text("Ngày: $dateStr", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            StatItem(
                                                label = "Có mặt",
                                                value = session.presentCount.toString(),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            StatItem(
                                                label = "Vắng",
                                                value = (session.totalStudents - session.presentCount).toString(),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            StatItem(
                                                label = "Tổng",
                                                value = session.totalStudents.toString(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Present list
                        val presentRecords = uiState.records.filter { it.status == AttendanceStatus.PRESENT }
                        val absentRecords = uiState.records.filter { it.status == AttendanceStatus.ABSENT }

                        if (presentRecords.isNotEmpty()) {
                            item {
                                Text(
                                    "✅ Có mặt (${presentRecords.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                            items(presentRecords) { record ->
                                AttendanceRecordItem(record = record)
                            }
                        }

                        if (absentRecords.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "❌ Vắng mặt (${absentRecords.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                            items(absentRecords) { record ->
                                AttendanceRecordItem(record = record)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AttendanceRecordItem(record: AttendanceRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (record.status == AttendanceStatus.PRESENT)
                    Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = if (record.status == AttendanceStatus.PRESENT)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.studentName, fontWeight = FontWeight.Medium)
                Text(
                    record.studentCode,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (record.status == AttendanceStatus.PRESENT && record.timestamp != null) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(record.timestamp.toDate())
                Text(timeStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

