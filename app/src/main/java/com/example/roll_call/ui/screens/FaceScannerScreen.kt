package com.example.roll_call.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roll_call.domain.model.Student
import com.example.roll_call.ui.viewmodel.AttendanceViewModel
import com.example.roll_call.ui.theme.*
import com.example.roll_call.utils.FaceRecognitionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceScannerScreen(
    classId: String,
    className: String,
    onFinish: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        viewModel.loadStudents(classId)
    }

    val faceHelper = remember { FaceRecognitionHelper(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var isProcessing by remember { mutableStateOf(false) }
    var showSessionNameDialog by remember { mutableStateOf(false) }
    var sessionNumber by remember { mutableStateOf("") }

    // Multi-frame voting: tích lũy kết quả nhận diện trước khi xác nhận
    val recognitionVotes = remember { mutableMapOf<String, Int>() }   // studentId -> count
    val VOTES_REQUIRED = 3
    val CONFIDENCE_THRESHOLD = 0.75f  // FaceNet 128-dim threshold
    // Cooldown per student: tránh nhận diện lại người vừa điểm danh
    val studentCooldowns = remember { mutableMapOf<String, Long>() }
    val COOLDOWN_MS = 5000L

    // Dialog nhập tên buổi
    if (showSessionNameDialog) {
        AlertDialog(
            onDismissRequest = { showSessionNameDialog = false },
            title = { Text("Tên buổi điểm danh") },
            text = {
                OutlinedTextField(
                    value = sessionNumber,
                    onValueChange = { sessionNumber = it },
                    placeholder = { Text("Nhập tên buổi (tùy chọn)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    showSessionNameDialog = false
                    viewModel.saveAttendance(classId, className, sessionNumber) { sessionIdResult ->
                        onFinish(sessionIdResult)
                    }
                }) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSessionNameDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            faceHelper.close()
            cameraExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Điểm danh: $className") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera Preview (60% chiều cao)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .background(Color.Black)
            ) {
                if (hasCameraPermission && !uiState.isLoading) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFaceDetected = { embedding ->
                            if (!isProcessing && uiState.students.isNotEmpty()) {
                                isProcessing = true
                                scope.launch {
                                    val student = faceHelper.recognizeStudent(
                                        embedding,
                                        uiState.students,
                                        threshold = CONFIDENCE_THRESHOLD
                                    )
                                    if (student != null) {
                                        val now = System.currentTimeMillis()
                                        val lastTime = studentCooldowns[student.id] ?: 0L
                                        if (now - lastTime > COOLDOWN_MS) {
                                            // Cộng vote cho student này
                                            val votes = (recognitionVotes[student.id] ?: 0) + 1
                                            recognitionVotes[student.id] = votes
                                            // Reset votes của student khác
                                            recognitionVotes.keys
                                                .filter { it != student.id }
                                                .forEach { recognitionVotes[it] = 0 }

                                            if (votes >= VOTES_REQUIRED && student.id !in uiState.presentStudents) {
                                                // Đủ vote → xác nhận điểm danh
                                                recognitionVotes[student.id] = 0
                                                studentCooldowns[student.id] = now
                                                viewModel.markPresent(student)
                                                delay(2000)
                                                viewModel.clearLastRecognized()
                                            }
                                        }
                                    } else {
                                        // Không nhận ra → reset tất cả votes
                                        recognitionVotes.clear()
                                    }
                                    delay(300) // throttle 300ms giữa các frame
                                    isProcessing = false
                                }
                            }
                        },
                        faceHelper = faceHelper,
                        cameraExecutor = cameraExecutor
                    )

                    // Overlay nhận diện
                    uiState.lastRecognized?.let { student ->
                        RecognitionOverlay(student = student)
                    }

                    // Loading indicator
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White
                        )
                    }
                } else if (!hasCameraPermission) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Cần quyền truy cập camera", color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Cấp quyền")
                        }
                    }
                }
            }

            // Danh sách điểm danh (40% chiều cao)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Đã điểm danh: ${uiState.presentStudents.size}/${uiState.students.size}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = {
                            showSessionNameDialog = true
                        },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Done, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lưu")
                        }
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val presentList = uiState.students.filter { it.id in uiState.presentStudents }
                    items(presentList) { student ->
                        PresentStudentChip(student = student)
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFaceDetected: (FloatArray) -> Unit,
    faceHelper: FaceRecognitionHelper,
    cameraExecutor: java.util.concurrent.ExecutorService
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setImageAnalysisAnalyzer(cameraExecutor) { imageProxy ->
                // Lấy bitmap TRƯỚC rồi close imageProxy ngay
                val bitmap = faceHelper.imageProxyToBitmap(imageProxy)
                imageProxy.close()
                if (bitmap == null) return@setImageAnalysisAnalyzer

                scope.launch {
                    try {
                        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                        val faces = faceHelper.getDetector().process(inputImage).await()
                        if (faces.isNotEmpty()) {
                            val face = faces.maxByOrNull {
                                it.boundingBox.width() * it.boundingBox.height()
                            } ?: return@launch

                            // Bỏ qua nếu khuôn mặt quá nhỏ (< 20% chiều rộng ảnh)
                            if (face.boundingBox.width() < bitmap.width * 0.2f) return@launch

                            val cropped = faceHelper.cropFace(bitmap, face) ?: return@launch

                            // Bỏ qua ảnh mờ / tối
                            if (!faceHelper.checkFaceQuality(cropped)) return@launch

                            val embedding = faceHelper.extractEmbedding(cropped)
                            onFaceDetected(embedding)
                        }
                    } catch (_: Exception) {}
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
            PreviewView(ctx).apply {
                controller = cameraController
            }
        },
        modifier = modifier
    )
}

@Composable
fun BoxScope.RecognitionOverlay(student: Student) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EduGreen)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    student.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    student.studentCode,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun PresentStudentChip(student: Student) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = EduBlueLight,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EduBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(student.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = EduTextPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                student.studentCode,
                fontSize = 12.sp,
                color = EduTextSecondary
            )
        }
    }
}

