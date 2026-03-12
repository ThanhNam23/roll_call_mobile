package com.example.roll_call.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.Student
import com.example.roll_call.ui.viewmodel.AttendanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentListScreen(
    classId: String,
    className: String,
    onStartAttendance: () -> Unit,
    onManageStudents: () -> Unit,
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(classId) {
        viewModel.loadStudents(classId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(className) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = onManageStudents) {
                        Icon(Icons.Default.PersonAdd, "Quản lý sinh viên")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.students.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onStartAttendance,
                    icon = { Icon(Icons.Default.PhotoCamera, null) },
                    text = { Text("Bắt đầu điểm danh") }
                )
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
                uiState.error != null -> {
                    Text(
                        "Lỗi: ${uiState.error}",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Column {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tổng sinh viên: ${uiState.students.size}", fontWeight = FontWeight.Medium)
                                Text(
                                    "Có embedding: ${uiState.students.count { it.faceEmbedding.isNotEmpty() }}",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.students) { student ->
                                StudentItem(student = student)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentItem(student: Student) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(student.name, fontWeight = FontWeight.Medium)
                Text(
                    student.studentCode,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (student.faceEmbedding.isNotEmpty()) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Đã đăng ký khuôn mặt",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

