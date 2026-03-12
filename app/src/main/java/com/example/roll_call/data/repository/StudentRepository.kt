package com.example.roll_call.data.repository

import com.example.roll_call.domain.model.Student
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class StudentRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getStudentsByClass(classId: String): Result<List<Student>> {
        return try {
            val snapshot = db.collection("classes")
                .document(classId)
                .collection("students")
                .get()
                .await()
            val students = snapshot.documents.map { doc ->
                val embeddingRaw = doc.get("faceEmbedding") as? List<*>
                val embedding = embeddingRaw?.mapNotNull {
                    when (it) {
                        is Double -> it.toFloat()
                        is Long -> it.toFloat()
                        is Float -> it
                        else -> null
                    }
                } ?: emptyList()
                Student(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    studentCode = doc.getString("studentCode") ?: "",
                    photoUrl = doc.getString("photoUrl") ?: "",
                    faceEmbedding = embedding
                )
            }
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addStudent(classId: String, name: String, studentCode: String): Result<Student> {
        return try {
            val data = mapOf(
                "name" to name,
                "studentCode" to studentCode,
                "photoUrl" to "",
                "faceEmbedding" to emptyList<Float>()
            )
            val ref = db.collection("classes")
                .document(classId)
                .collection("students")
                .add(data)
                .await()
            Result.success(Student(id = ref.id, name = name, studentCode = studentCode))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFaceEmbedding(classId: String, studentId: String, embedding: FloatArray): Result<Unit> {
        return try {
            db.collection("classes")
                .document(classId)
                .collection("students")
                .document(studentId)
                .update("faceEmbedding", embedding.toList())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

