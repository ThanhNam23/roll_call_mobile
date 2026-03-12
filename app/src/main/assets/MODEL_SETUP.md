# Hướng dẫn tải ArcFace TFLite Model

## Tại sao cần ArcFace?

| | ML Kit (chỉ detect) | ML Kit + ArcFace |
|---|---|---|
| Phát hiện khuôn mặt | ✅ | ✅ |
| Nhận dạng danh tính | ❌ | ✅ |
| Độ chính xác nhận dạng | N/A | ~99% (LFW benchmark) |

---

## Bước 1: Tải model

### Option A — MobileFaceNet (~5MB, nhanh hơn)
```
https://github.com/nicehuster/cft-arcface/releases
→ Tải: mobilefacenet.tflite
→ Đổi tên thành: arcface.tflite
```

### Option B — ArcFace ResNet50 (~100MB, chính xác hơn)
```
https://tfhub.dev/google/lite-model/arc-face/1
→ Tải file .tflite
→ Đổi tên thành: arcface.tflite
```

### Option C — InsightFace MobileFaceNet (khuyên dùng, ~4MB)
Tải từ repo InsightFace:
```
https://github.com/deepinsight/insightface/tree/master/recognition
```

---

## Bước 2: Đặt file model

```
app/
  src/
    main/
      assets/
        arcface.tflite   ← đặt vào đây
```

---

## Bước 3: Đăng ký khuôn mặt sinh viên

Khi đăng ký khuôn mặt SV trên app/website, cần lưu **face embedding** vào Firestore:

### Cấu trúc Firestore

```
classes/{classId}/students/{studentId}
├── name: "Nguyễn Văn A"
├── studentCode: "21521000"
├── photoUrl: "https://..."
└── faceEmbedding: [0.12, -0.34, 0.56, ...]  ← FloatArray(512)
```

### Flow đăng ký
1. Chụp ảnh SV (3-5 ảnh góc khác nhau)
2. ML Kit detect khuôn mặt → crop
3. ArcFace extract embedding (512 chiều)
4. **Average** các embeddings → lưu lên Firestore

---

## Bước 4: Điều chỉnh threshold

Trong `FaceRecognitionHelper.kt`:
```kotlin
const val SIMILARITY_THRESHOLD = 0.6f  // Tăng lên để chặt hơn (giảm false positive)
                                         // Giảm xuống nếu hay miss (giảm false negative)
```

| Threshold | Ý nghĩa |
|---|---|
| 0.5 | Rộng — dễ nhận nhưng có thể nhầm |
| **0.6** | **Khuyến nghị** — cân bằng tốt |
| 0.7 | Chặt — ít nhầm nhưng có thể miss |

---

## Lưu ý khi điểm danh

- Cần **đủ ánh sáng** để ML Kit detect được khuôn mặt
- Nên đăng ký ảnh SV ở **nhiều góc độ** (thẳng, trái, phải)
- Mỗi lần nhận diện thành công có **cooldown 2 giây** để tránh điểm danh 2 lần

