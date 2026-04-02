package com.example.roll_call.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roll_call.data.repository.AttendanceRepository
import com.example.roll_call.data.repository.AuthRepository
import com.example.roll_call.data.repository.StudentRepository
import com.example.roll_call.domain.model.AttendanceRecord
import com.example.roll_call.domain.model.AttendanceStatus
import com.example.roll_call.domain.model.Student
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val students: List<Student> = emptyList(),
    val presentStudents: Set<String> = emptySet(),   // studentId
    val lastRecognized: Student? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val sessionId: String? = null,
    val isEditingSession: Boolean = false,  // true nếu tiếp tục điểm danh session hiện có
    val teacherFaceEmbedding: List<Float>? = null,
    val showTeacherMessage: Boolean = false
)

class AttendanceViewModel : ViewModel() {
    private val studentRepository = StudentRepository()
    private val attendanceRepository = AttendanceRepository()
    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState

    fun loadStudents(classId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = studentRepository.getStudentsByClass(classId)
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(students = it, isLoading = false) },
                onFailure = { _uiState.value.copy(error = it.message, isLoading = false) }
            )
        }
    }

    fun createNewSession(classId: String, className: String, onSessionCreated: (String) -> Unit) {
        val teacherId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = studentRepository.getStudentsByClass(classId)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.exceptionOrNull()?.message)
                return@launch
            }

            val students = result.getOrThrow()
            val sessionResult = attendanceRepository.createSession(
                classId, className, "", teacherId, students.size
            )

            sessionResult.fold(
                onSuccess = { sessionId ->
                    _uiState.value = _uiState.value.copy(
                        students = students,
                        isLoading = false,
                        sessionId = sessionId,
                        isEditingSession = false
                    )
                    onSessionCreated(sessionId)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.message
                    )
                }
            )
        }
    }

    fun loadStudentsForSession(classId: String, sessionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isEditingSession = true, sessionId = sessionId)
            try {
                // Load sinh viên của lớp
                val studentsResult = studentRepository.getStudentsByClass(classId)
                android.util.Log.d("AttendanceVM", "loadStudentsForSession classId=$classId, result=${studentsResult.isSuccess}, size=${studentsResult.getOrNull()?.size ?: 0}")

                if (studentsResult.isFailure) {
                    val error = studentsResult.exceptionOrNull()?.message ?: "Unknown error"
                    android.util.Log.e("AttendanceVM", "Failed to load students: $error")
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error)
                    return@launch
                }
                val students = studentsResult.getOrThrow()
                android.util.Log.d("AttendanceVM", "Loaded ${students.size} students")

                // Load records đã có của session này
                var presentIds = emptySet<String>()
                val recordsResult = attendanceRepository.getRecords(sessionId)
                if (recordsResult.isSuccess) {
                    val records = recordsResult.getOrThrow()
                    // Cập nhật presentStudents từ records có status PRESENT
                    presentIds = records
                        .filter { it.status == AttendanceStatus.PRESENT }
                        .map { it.studentId }
                        .toSet()
                    android.util.Log.d("AttendanceVM", "Loaded ${presentIds.size} present records")
                } else {
                    android.util.Log.w("AttendanceVM", "Failed to load records: ${recordsResult.exceptionOrNull()?.message}")
                }

                android.util.Log.d("AttendanceVM", "Updating UI: students=${students.size}, present=${presentIds.size}")
                _uiState.value = _uiState.value.copy(
                    students = students,
                    presentStudents = presentIds,
                    isLoading = false
                )
            } catch (e: Exception) {
                android.util.Log.e("AttendanceVM", "Exception in loadStudentsForSession", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun markPresent(student: Student) {
        val updated = _uiState.value.presentStudents + student.id
        _uiState.value = _uiState.value.copy(
            presentStudents = updated,
            lastRecognized = student
        )
    }

    fun saveRecognition(sessionId: String, student: Student) {
        viewModelScope.launch {
            val record = AttendanceRecord(
                studentId = student.id,
                studentName = student.name,
                studentCode = student.studentCode,
                status = AttendanceStatus.PRESENT,
                timestamp = Timestamp.now()
            )
            attendanceRepository.saveRecognition(sessionId, record)
        }
    }

    fun toggleAttendance(studentId: String) {
        val current = _uiState.value.presentStudents
        val updated = if (studentId in current) current - studentId else current + studentId
        _uiState.value = _uiState.value.copy(presentStudents = updated)
    }

    fun clearLastRecognized() {
        _uiState.value = _uiState.value.copy(lastRecognized = null)
    }

    fun saveAttendance(classId: String, className: String, sessionNumber: String, onSuccess: (String) -> Unit) {
        val teacherId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val students = _uiState.value.students
            val presentIds = _uiState.value.presentStudents

            val sessionResult = attendanceRepository.createSession(
                classId, className, sessionNumber, teacherId, students.size
            )
            sessionResult.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
                return@launch
            }

            val sessionId = sessionResult.getOrThrow()
            val records = students.map { student ->
                AttendanceRecord(
                    studentId = student.id,
                    studentName = student.name,
                    studentCode = student.studentCode,
                    status = if (student.id in presentIds) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT,
                    timestamp = if (student.id in presentIds) Timestamp.now() else null
                )
            }


            val saveResult = attendanceRepository.saveAttendanceRecords(sessionId, records)
            _uiState.value = _uiState.value.copy(isSaving = false)
            saveResult.fold(
                onSuccess = { onSuccess(sessionId) },
                onFailure = { _uiState.value = _uiState.value.copy(error = it.message) }
            )
        }
    }

    fun loadTeacherFaceProfile() {
        val teacherId = authRepository.currentUserId ?: return
        android.util.Log.d("AttendanceVM", "=== LOADING TEACHER PROFILE START ===")
        android.util.Log.d("AttendanceVM", "Current User ID: $teacherId")

        viewModelScope.launch {
            try {
                android.util.Log.d("AttendanceVM", "Loading teacher face profile for: $teacherId")
                val teacherRepository = com.example.roll_call.data.repository.TeacherRepository()

                // Try to load, with retry
                var attempts = 0
                var profile: com.example.roll_call.domain.model.TeacherFaceProfile? = null

                while (attempts < 3 && profile == null) {
                    val result = teacherRepository.getTeacherFaceProfile(teacherId)
                    result.fold(
                        onSuccess = { loadedProfile ->
                            if (loadedProfile != null && loadedProfile.faceEmbedding.isNotEmpty()) {
                                profile = loadedProfile
                                val embSize = loadedProfile.faceEmbedding.size
                                val firstVal = loadedProfile.faceEmbedding.firstOrNull() ?: 0f
                                val lastVal = loadedProfile.faceEmbedding.lastOrNull() ?: 0f
                                android.util.Log.d("AttendanceVM", "✓✓✓ TEACHER FACE LOADED SUCCESSFULLY ✓✓✓")
                                android.util.Log.d("AttendanceVM", "  Size: $embSize, First: $firstVal, Last: $lastVal")
                                android.util.Log.d("AttendanceVM", "  Teacher Name: ${loadedProfile.name}, Email: ${loadedProfile.email}")
                                _uiState.value = _uiState.value.copy(teacherFaceEmbedding = loadedProfile.faceEmbedding)
                            } else {
                                android.util.Log.d("AttendanceVM", "✗ Teacher profile empty or null (attempt ${attempts + 1})")
                                android.util.Log.d("AttendanceVM", "  Loaded profile: $loadedProfile")
                                if (loadedProfile != null) {
                                    android.util.Log.d("AttendanceVM", "  Profile exists but embedding is empty! Size: ${loadedProfile.faceEmbedding.size}")
                                }
                            }
                        },
                        onFailure = { error ->
                            android.util.Log.e("AttendanceVM", "✗✗✗ FAILED TO LOAD PROFILE (attempt ${attempts + 1}) ✗✗✗")
                            android.util.Log.e("AttendanceVM", "  Error: ${error.message}")
                            android.util.Log.e("AttendanceVM", "  Exception type: ${error::class.simpleName}")
                        }
                    )

                    attempts++
                    if (profile == null && attempts < 3) {
                        android.util.Log.d("AttendanceVM", "  Retrying... (${attempts}/3)")
                        delay(500)  // Delay before retry
                    }
                }

                if (profile == null) {
                    android.util.Log.e("AttendanceVM", "❌ COULD NOT LOAD TEACHER FACE AFTER 3 ATTEMPTS ❌")
                    android.util.Log.e("AttendanceVM", "  Teacher ID: $teacherId")
                    android.util.Log.e("AttendanceVM", "  Check: 1. Firestore has collection 'teacherFaceProfiles'?")
                    android.util.Log.e("AttendanceVM", "  Check: 2. Document with ID '$teacherId' exists?")
                    android.util.Log.e("AttendanceVM", "  Check: 3. faceEmbedding field has data?")
                    android.util.Log.e("AttendanceVM", "  Check: 4. Firestore Rules allow read?")
                } else {
                    android.util.Log.d("AttendanceVM", "=== LOADING COMPLETE: TEACHER FACE READY ===")
                }
            } catch (e: Exception) {
                android.util.Log.e("AttendanceVM", "❌ EXCEPTION LOADING TEACHER PROFILE ❌", e)
            }
        }
    }

    fun showTeacherMessage() {
        _uiState.value = _uiState.value.copy(showTeacherMessage = true)
    }

    fun clearTeacherMessage() {
        _uiState.value = _uiState.value.copy(showTeacherMessage = false)
    }
}

