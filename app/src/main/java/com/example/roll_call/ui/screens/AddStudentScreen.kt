package com.example.roll_call.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.Student
import com.example.roll_call.ui.viewmodel.StudentManagementViewModel
import com.example.roll_call.utils.FaceRecognitionHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentScreen(
    classId: String,
    className: String,
    onBack: () -> Unit,
    viewModel: StudentManagementViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showFaceDialog by remember { mutableStateOf(false) }
    var selectedStudent by remember { mutableStateOf<Student?>(null) }

    LaunchedEffect(classId) { viewModel.loadStudents(classId) }

    // Hiện snackbar khi có success/error
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.successMessage, uiState.error) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.error?.let {
            snackbarHostState.showSnackbar("Lỗi: $it")
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý sinh viên - $className") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Thêm sinh viên")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.students.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Person, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Chưa có sinh viên nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showAddDialog = true }) { Text("Thêm sinh viên đầu tiên") }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.students) { student ->
                            StudentManagementItem(
                                student = student,
                                onRegisterFace = {
                                    selectedStudent = student
                                    showFaceDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog thêm sinh viên
    if (showAddDialog) {
        AddStudentDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, code ->
                viewModel.addStudent(classId, name, code)
                showAddDialog = false
            }
        )
    }

    // Dialog đăng ký khuôn mặt
    if (showFaceDialog && selectedStudent != null) {
        FaceRegistrationDialog(
            student = selectedStudent!!,
            classId = classId,
            onDismiss = { showFaceDialog = false },
            onEmbeddingSaved = { embedding ->
                viewModel.saveFaceEmbedding(classId, selectedStudent!!.id, embedding)
                showFaceDialog = false
            }
        )
    }
}

@Composable
fun StudentManagementItem(student: Student, onRegisterFace: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(student.name, fontWeight = FontWeight.Medium)
                Text(student.studentCode, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (student.faceEmbedding.isNotEmpty()) {
                Icon(Icons.Default.CheckCircle, "Đã đăng ký",
                    tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            OutlinedButton(
                onClick = onRegisterFace,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Face, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (student.faceEmbedding.isNotEmpty()) "Cập nhật" else "Đăng ký",
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun AddStudentDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var studentCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm sinh viên") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Họ và tên") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = studentCode,
                    onValueChange = { studentCode = it },
                    label = { Text("Mã sinh viên") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, studentCode) },
                enabled = name.isNotBlank() && studentCode.isNotBlank()
            ) { Text("Thêm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
fun FaceRegistrationDialog(
    student: Student,
    classId: String,
    onDismiss: () -> Unit,
    onEmbeddingSaved: (FloatArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    val faceHelper = remember { FaceRecognitionHelper(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val capturedEmbeddings = remember { mutableStateListOf<FloatArray>() }
    var isCapturing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("📷 Chụp 5 ảnh ở các góc khác nhau để tăng độ chính xác") }

    // AtomicBoolean để đọc an toàn từ background thread của analyzer
    val shouldCapture = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            faceHelper.close()
            cameraExecutor.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Đăng ký khuôn mặt", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(student.name, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                ) {
                    if (hasCameraPermission) {
                        val cameraController = remember {
                            LifecycleCameraController(context).apply {
                                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                setImageAnalysisAnalyzer(cameraExecutor) { imageProxy ->
                                    if (shouldCapture.compareAndSet(true, false)) {
                                        // Lấy bitmap TRƯỚC khi close imageProxy
                                        val bitmap = faceHelper.imageProxyToBitmap(imageProxy)
                                        imageProxy.close()
                                        scope.launch {
                                            if (bitmap == null) {
                                                statusText = "❌ Lỗi đọc ảnh, thử lại"
                                                isCapturing = false
                                                return@launch
                                            }
                                            try {
                                                // Detect face từ bitmap (InputImage.fromBitmap, không cần imageProxy)
                                                val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                                                val faces = faceHelper.getDetector().process(inputImage).await()
                                                val face = faces.firstOrNull()
                                                if (face != null) {
                                                    val cropped = faceHelper.cropFace(bitmap, face)
                                                    if (cropped != null) {
                                                        val embedding = faceHelper.extractEmbedding(cropped)
                                                        capturedEmbeddings.add(embedding)
                                                        statusText = "✅ Đã chụp ${capturedEmbeddings.size}/3 ảnh"
                                                    } else {
                                                        statusText = "❌ Không thấy khuôn mặt rõ, thử lại"
                                                    }
                                                } else {
                                                    statusText = "❌ Không phát hiện khuôn mặt, thử lại"
                                                }
                                            } catch (e: Exception) {
                                                statusText = "❌ Lỗi xử lý: ${e.message}"
                                            }
                                            isCapturing = false
                                        }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }
                        }

                        DisposableEffect(lifecycleOwner) {
                            cameraController.bindToLifecycle(lifecycleOwner)
                            onDispose { cameraController.unbind() }
                        }

                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply { controller = cameraController }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Status overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(statusText, color = Color.White, fontSize = 12.sp)
                        }

                        // Nút chụp
                        Button(
                                            onClick = {
                                                if (!isCapturing) {
                                                    isCapturing = true
                                                    statusText = "📸 Đang chụp..."
                                                    shouldCapture.set(true)
                                                }
                                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            enabled = !isCapturing
                        ) {
                            Text("Chụp (${capturedEmbeddings.size}/3)")
                        }

                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Cần quyền camera", color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Cấp quyền")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (capturedEmbeddings.size > 0) {
                    LinearProgressIndicator(
                        progress = { capturedEmbeddings.size / 5f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${capturedEmbeddings.size}/5 ảnh đã chụp — chụp nhiều góc khác nhau",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = {
                            val avgEmbedding = averageEmbeddings(capturedEmbeddings)
                            onEmbeddingSaved(avgEmbedding)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = capturedEmbeddings.size >= 1
                    ) {
                        Text("Lưu khuôn mặt (${capturedEmbeddings.size} ảnh)")
                    }
                }
            }
        }
    }
}

/**
 * Tính trung bình cộng của nhiều embedding vectors
 */
fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
    if (embeddings.isEmpty()) return FloatArray(512)
    val size = embeddings[0].size
    val result = FloatArray(size)
    for (emb in embeddings) {
        for (i in 0 until size) result[i] += emb[i]
    }
    for (i in 0 until size) result[i] /= embeddings.size
    // L2 normalize
    var norm = 0f
    for (v in result) norm += v * v
    norm = kotlin.math.sqrt(norm)
    return if (norm == 0f) result else FloatArray(size) { result[it] / norm }
}

