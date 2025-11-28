// kotlin
package com.yeab.esnapp.util

import android.graphics.Bitmap
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.math.BigInteger

object ImageSimilarityUtils {

    /**
     * Gelen resmin phash'ini hesaplayıp Firebase'deki phash'lerle karşılaştırır.
     * Eşik (threshold) altında olan tüm eşleşmeleri callback ile döner.
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
            val incomingPhash = averageHash(bitmap)

            val dbRef = FirebaseDatabase.getInstance().reference
                .child("image_hashes")
                .child(merchantUid)

            dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val matches = ArrayList<MatchResult>()

                        for (child in snapshot.children) {
                            val otherPhash = child.child("phash").getValue(String::class.java)
                            val otherImageUrl = child.child("imageUrl").getValue(String::class.java)
                            val imageId = child.key ?: continue
                            if (otherPhash != null) {
                                val dist = try {
                                    hammingDistanceHex(incomingPhash, otherPhash)
                                } catch (e: Exception) {
                                    Int.MAX_VALUE
                                }
                                if(threshold - dist >= 0){
                                    val percentage = ((threshold - dist).toDouble()  * 5).toInt()
                                    matches.add(MatchResult(imageId, otherImageUrl, otherPhash, dist, percentage))
                                }
                            }
                        }

                        // En iyi eşleşmeleri uzaklığa göre sırala
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
}
