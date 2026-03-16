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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.example.roll_call.ui.theme.*
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
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showFaceDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedStudent by remember { mutableStateOf<Student?>(null) }

    LaunchedEffect(classId) { viewModel.loadStudents(classId) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.successMessage, uiState.error) {
        uiState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        uiState.error?.let { snackbarHostState.showSnackbar("⚠️ $it"); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý SV - $className") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EduSurface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = EduBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Thêm sinh viên")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.students.isEmpty() -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Chưa có sinh viên nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showAddDialog = true }) { Text("Thêm sinh viên đầu tiên") }
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.students, key = { it.id }) { student ->
                            StudentManagementItem(
                                student = student,
                                onRegisterFace = { selectedStudent = student; showFaceDialog = true },
                                onEdit = { selectedStudent = student; showEditDialog = true },
                                onDelete = { selectedStudent = student; showDeleteDialog = true }
                            )
                        }
                    }
                }
            }
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // Dialog thêm
    if (showAddDialog) {
        StudentFormDialog(
            title = "Thêm sinh viên",
            initialName = "",
            initialCode = "",
            confirmText = "Thêm",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, code ->
                viewModel.addStudent(classId, name, code)
                showAddDialog = false
            }
        )
    }

    // Dialog sửa
    if (showEditDialog && selectedStudent != null) {
        StudentFormDialog(
            title = "Chỉnh sửa sinh viên",
            initialName = selectedStudent!!.name,
            initialCode = selectedStudent!!.studentCode,
            confirmText = "Lưu",
            onDismiss = { showEditDialog = false },
            onConfirm = { name, code ->
                viewModel.updateStudent(classId, selectedStudent!!.id, name, code)
                showEditDialog = false
            }
        )
    }

    // Dialog xác nhận xóa
    if (showDeleteDialog && selectedStudent != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Xóa sinh viên", color = EduTextPrimary) },
            text = { Text("Bạn có chắc muốn xóa sinh viên \"${selectedStudent!!.name}\" (${selectedStudent!!.studentCode})?", color = EduTextSecondary) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteStudent(classId, selectedStudent!!.id); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EduRed)
                ) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Hủy") } },
            containerColor = EduSurface,
            titleContentColor = EduTextPrimary,
            textContentColor = EduTextSecondary
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
fun StudentManagementItem(
    student: Student,
    onRegisterFace: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EduSurface),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = EduTextSecondary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(student.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = EduTextPrimary)
                    Text(student.studentCode, fontSize = 12.sp, color = EduTextSecondary)
                }
                if (student.faceEmbedding.isNotEmpty()) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Đã đăng ký khuôn mặt",
                        tint = EduGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Nút đăng ký khuôn mặt
                OutlinedButton(
                    onClick = onRegisterFace,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Face, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (student.faceEmbedding.isNotEmpty()) "Cập nhật KM" else "Đăng ký KM", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                // Nút sửa
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "Sửa", tint = EduBlue, modifier = Modifier.size(18.dp))
                }
                // Nút xóa
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Xóa", tint = EduRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun StudentFormDialog(
    title: String,
    initialName: String,
    initialCode: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var studentCode by remember { mutableStateOf(initialCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = EduTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Họ và tên") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EduBlue,
                        unfocusedBorderColor = EduBorder
                    )
                )
                OutlinedTextField(
                    value = studentCode,
                    onValueChange = { studentCode = it },
                    label = { Text("Mã sinh viên (duy nhất)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Mã sinh viên phải là duy nhất trong lớp") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EduBlue,
                        unfocusedBorderColor = EduBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, studentCode) },
                enabled = name.isNotBlank() && studentCode.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EduBlue)
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
        containerColor = EduSurface,
        titleContentColor = EduTextPrimary,
        textContentColor = EduTextSecondary
    )
}

@Composable
fun AddStudentDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    StudentFormDialog("Thêm sinh viên", "", "", "Thêm", onDismiss, onConfirm)
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
    var statusText by remember { mutableStateOf("") }

    // AtomicBoolean để đọc an toàn từ background thread của analyzer
    val shouldCapture = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            faceHelper.close()
            cameraExecutor.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = EduSurface),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Đăng ký khuôn mặt",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = EduTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        student.name,
                        color = EduBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Camera preview box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .border(2.dp, EduBlue, RoundedCornerShape(12.dp))
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
                                                        statusText = "✅ Đã chụp ${capturedEmbeddings.size} ảnh"
                                                    } else {
                                                        statusText = "❌ Không thấy khuôn mặt rõ, thử lại"
                                                    }
                                                } else {
                                                    statusText = "❌ Không phát hiện khuôn mặt"
                                                }
                                            } catch (e: Exception) {
                                                statusText = "❌ Lỗi: ${e.message?.take(30)}"
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

                        // Guide overlay with face frame
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Hướng dẫn căn giữa
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val faceWidth = width * 0.6f
                                val faceHeight = height * 0.7f
                                val left = (width - faceWidth) / 2
                                val top = (height - faceHeight) / 2

                                // Vẽ hộp hướng dẫn
                                drawRect(
                                    color = Color.White,
                                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(faceWidth, faceHeight),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                )
                            }

                            // Status overlay - bottom center
                            if (statusText.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(12.dp)
                                        .background(Color(0xDD000000), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        statusText,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Nút chụp - dưới phải
                            Button(
                                onClick = {
                                    if (!isCapturing && capturedEmbeddings.size < 3) {
                                        isCapturing = true
                                        statusText = "📸 Đang chụp..."
                                        shouldCapture.set(true)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp),
                                enabled = !isCapturing && capturedEmbeddings.size < 3,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EduBlue,
                                    disabledContainerColor = EduBlue.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    "📸 Chụp",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                    } else {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Face,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Cần quyền truy cập camera", color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = EduBlue)
                            ) {
                                Text("Cấp quyền camera")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress section
                if (capturedEmbeddings.size > 0) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { (capturedEmbeddings.size / 3f).coerceAtMost(1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = EduBlue,
                            trackColor = EduBorder
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${capturedEmbeddings.size}/3 ảnh",
                            fontSize = 12.sp,
                            color = EduTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Hủy", fontWeight = FontWeight.Medium, color = EduBlue)
                    }
                    Button(
                        onClick = {
                            val avgEmbedding = averageEmbeddings(capturedEmbeddings)
                            onEmbeddingSaved(avgEmbedding)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        enabled = capturedEmbeddings.size >= 1,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EduGreen,
                            disabledContainerColor = EduBorder
                        )
                    ) {
                        Text(
                            "✓ Lưu",
                            fontWeight = FontWeight.Medium
                        )
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

