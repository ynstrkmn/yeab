package com.yeab.esnapp.util

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.math.BigInteger

object ImageSimilarityUtils {

    /**
     * Gelen resmin phash'ini (artık 8 farklı açı için) hesaplayıp
     * Firebase'deki phash'lerle karşılaştırır.
     *
     * - Bitmap, 0°, 45°, 90°, 135°, 180°, 225°, 270°, 315° açılarıyla döndürülerek
     *   8 farklı pHash üretilir.
     * - Her Firebase kaydı için bu 8 hash'e göre MIN Hamming mesafesi alınır.
     * - Eğer minDistance <= threshold ise, kayıt MatchResult listesine eklenir.
     *
     * onResult -> eşleşen kayıtların listesi (boş olabilir)
     * onError  -> hata varsa çağrılır
     *
     * Bu method UI thread üzerinde Firebase callback'leri kullanacağından güvenlidir.
     */
    @JvmStatic
    fun calculateSimilarity(
        bitmap: Bitmap,
        merchantUid: String,
        threshold: Int = 10,
        onResult: (List<MatchResult>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        try {
            // 1) Gelen bitmap için 8 farklı açıdan pHash üret
            val rotatedHashes = mutableListOf<String>()

            val angles = listOf(
                0f,
                45f,
                90f,
                135f,
                180f,
                225f,
                270f,
                315f
            )

            for (angle in angles) {
                try {
                    val bmpToHash: Bitmap =
                        if (angle == 0f) {
                            bitmap
                        } else {
                            rotateBitmap(bitmap, angle)
                        }

                    val hash = averageHash(bmpToHash)
                    rotatedHashes.add(hash)

                    // 0° olan orijinal bitmap'i recycle etmiyoruz, diğerlerini edebiliriz
                    if (angle != 0f && bmpToHash != bitmap) {
                        bmpToHash.recycle()
                    }
                } catch (_: Exception) {
                    // Bu açıda bir problem olursa sadece o açıyı atla
                }
            }

            if (rotatedHashes.isEmpty()) {
                onError?.invoke(Exception("Failed to generate hashes for input bitmap"))
                return
            }

            // 2) Firebase'den ilgili merchant için image_hashes node'unu oku
            val dbRef = FirebaseDatabase.getInstance().reference
                .child("image_hashes")
                .child(merchantUid)

            dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val matches = ArrayList<MatchResult>()

                        for (child in snapshot.children) {
                            val otherPhash = child.child("phash").getValue(String::class.java)
                            val otherImageUrl =
                                child.child("imageUrl").getValue(String::class.java)
                            val imageId = child.key ?: continue

                            if (!otherPhash.isNullOrEmpty()) {
                                var minDistance = Int.MAX_VALUE

                                // Bu kayıt için tüm açılardaki hash’lere göre min distance hesapla
                                for (incomingHash in rotatedHashes) {
                                    val dist = try {
                                        hammingDistanceHex(incomingHash, otherPhash)
                                    } catch (e: Exception) {
                                        Int.MAX_VALUE
                                    }

                                    if (dist < minDistance) {
                                        minDistance = dist
                                    }
                                }

                                if (minDistance != Int.MAX_VALUE && threshold - minDistance >= 0) {
                                    val percentage =
                                        ((threshold - minDistance).toDouble() * 5).toInt()
                                    matches.add(
                                        MatchResult(
                                            imageId = imageId,
                                            imageUrl = otherImageUrl,
                                            phash = otherPhash,
                                            distance = minDistance,
                                            percentage = percentage
                                        )
                                    )
                                }
                            }
                        }

                        // En iyi eşleşmeleri uzaklığa göre sırala (distance küçükten büyüğe)
                        matches.sortBy { it.distance }
                        onResult(matches)
                    } catch (e: Exception) {
                        onError?.invoke(e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onError?.invoke(Exception(error.message))
                }
            })
        } catch (e: Exception) {
            onError?.invoke(e)
        }
    }

    // Basit average hash (aHash): resmi 8x8 küçült, grayscale, ortalama değere göre bit dizisi oluştur.
    private fun averageHash(src: Bitmap): String {
        val size = 8
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        val luminances = IntArray(pixels.size)
        var sum = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            luminances[i] = lum
            sum += lum
        }
        val avg = if (luminances.isNotEmpty()) sum / luminances.size else 0

        val bits = StringBuilder()
        for (lum in luminances) {
            bits.append(if (lum >= avg) '1' else '0')
        }

        // 64 bit -> hex (16 chars)
        val bigInt = BigInteger(bits.toString(), 2)
        return String.format("%016x", bigInt)
    }

    // Hex halinde verilen iki hash'in Hamming mesafesini hesaplar
    private fun hammingDistanceHex(hex1: String, hex2: String): Int {
        val b1 = BigInteger(hex1, 16)
        val b2 = BigInteger(hex2, 16)
        val xor = b1.xor(b2)
        return xor.bitCount()
    }

    // Bitmap'i verilen açı kadar döndürür
    private fun rotateBitmap(src: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
}
