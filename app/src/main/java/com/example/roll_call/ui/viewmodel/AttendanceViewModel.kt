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
    val sessionId: String? = null
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

    fun markPresent(student: Student) {
        val updated = _uiState.value.presentStudents + student.id
        _uiState.value = _uiState.value.copy(
            presentStudents = updated,
            lastRecognized = student
        )
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
}

