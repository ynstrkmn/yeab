package com.yeab.esnapp.util

import android.graphics.Bitmap
import com.google.firebase.database.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

object ImageSimilarityUtils {

    /**
     * Yeni mantık:
     *
     * 1) Gelen bitmap'ten ML Kit ile metni çıkar (queryText).
     * 2) image_hashes/{merchantUid} altındaki her kayıt için 'recognizedText' alanını oku.
     * 3) Query metni ile kayıt metni arasında kelime bazlı benzerlik (Jaccard) hesapla.
     * 4) Yüzde (0..100) olarak hesaplanan similarity threshold'den büyükse MatchResult'a ekle.
     *
     * threshold parametresini "minimum gerekli benzerlik yüzdesi" (örn. 40, 50) olarak düşünüyoruz.
     */
    @JvmStatic
    fun calculateSimilarity(
        bitmap: Bitmap,
        merchantUid: String,
        threshold: Int = 20,
        onResult: (List<MatchResult>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text ?: ""
                    val queryText = rawText.trim()
                    if (queryText.isEmpty()) {
                        onResult(emptyList())
                        return@addOnSuccessListener
                    }

                    val queryTokens = normalizeTokens(queryText)
                    if (queryTokens.isEmpty()) {
                        onResult(emptyList())
                        return@addOnSuccessListener
                    }

                    val minPercent = threshold.coerceIn(1, 100)

                    val dbRef = FirebaseDatabase.getInstance().reference
                        .child("image_hashes")
                        .child(merchantUid)

                    dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            try {
                                val matches = ArrayList<MatchResult>()

                                for (child in snapshot.children) {
                                    val storedText =
                                        child.child("recognizedText")
                                            .getValue(String::class.java)
                                    val otherImageUrl =
                                        child.child("imageUrl").getValue(String::class.java)
                                    val imageId = child.key ?: continue

                                    if (storedText.isNullOrBlank()) continue

                                    val storedTokens = normalizeTokens(storedText)
                                    if (storedTokens.isEmpty()) continue

                                    val sim = tokenSetSimilarity(queryTokens, storedTokens)

                                    val percentage = (sim * 100).toInt()

                                    if (percentage >= minPercent) {
                                        val distance = 100 - percentage

                                        // phash alanını şimdilik boş geçiyoruz (veya DB'den okursun)
                                        val match = MatchResult(
                                            imageId = imageId,
                                            imageUrl = otherImageUrl,
                                            phash = "", // eski anlamı şimdilik kullanılmıyor
                                            distance = distance,
                                            percentage = percentage
                                        )
                                        matches.add(match)
                                    }
                                }

                                // En iyi eşleşmeler: distance küçükten büyüğe
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
                }
                .addOnFailureListener { e ->
                    onError?.invoke(e)
                }

        } catch (e: Exception) {
            onError?.invoke(e)
        }
    }

    fun calculateSimilarityFromText(
        text: String,
        merchantUid: String,
        threshold: Int = 20,
        onResult: (List<MatchResult>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        try {
            val queryText = text.trim()
            if (queryText.isEmpty()) {
                onResult(emptyList())
                return
            }

            val queryTokens = normalizeTokens(queryText)
            if (queryTokens.isEmpty()) {
                onResult(emptyList())
                return
            }

            val minPercent = threshold.coerceIn(1, 100)

            val dbRef = FirebaseDatabase.getInstance().reference
                .child("image_hashes")
                .child(merchantUid)

            dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val matches = ArrayList<MatchResult>()

                        for (child in snapshot.children) {
                            val storedText =
                                child.child("recognizedText").getValue(String::class.java)
                            val otherImageUrl =
                                child.child("imageUrl").getValue(String::class.java)
                            val imageId = child.key ?: continue

                            if (storedText.isNullOrBlank()) continue

                            val storedTokens = normalizeTokens(storedText)
                            if (storedTokens.isEmpty()) continue

                            val sim = tokenSetSimilarity(queryTokens, storedTokens)
                            val percentage = (sim * 100).toInt()

                            if (percentage >= minPercent) {
                                val distance = 100 - percentage
                                matches.add(
                                    MatchResult(
                                        imageId = imageId,
                                        imageUrl = otherImageUrl,
                                        phash = "",
                                        distance = distance,
                                        percentage = percentage
                                    )
                                )
                            }
                        }

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

    /**
     * Metni normalize edip kelime seti döner:
     * - Büyük harfe çevirir
     * - Harf/rakam dışını boşluğa çevirir
     * - 2 karakterden kısa token'ları atar
     */
    private fun normalizeTokens(text: String): Set<String> {
        return text
            .uppercase(Locale.getDefault())
            .replace("[^A-Z0-9]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.length >= 2 }
            .toSet()
    }

    /**
     * Token setleri için daha zeki benzerlik:
     *
     * - Her token için karşı tarafta en benzer kelimeyi bulur (Levenshtein tabanlı).
     * - A'daki tüm tokenlar için avg(maxSim(a_i, B)) hesaplanır => simAB
     * - B'deki tüm tokenlar için avg(maxSim(b_j, A)) hesaplanır => simBA
     * - Sonuç = (simAB + simBA) / 2  (0.0 .. 1.0)
     */
    private fun tokenSetSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0

        // A'nın her elemanı için B'deki en yüksek token benzerliği
        val simAB = a.map { tokenA ->
            b.maxOfOrNull { tokenB -> tokenSimilarity(tokenA, tokenB) } ?: 0.0
        }.average()

        // B'nin her elemanı için A'daki en yüksek token benzerliği
        val simBA = b.map { tokenB ->
            a.maxOfOrNull { tokenA -> tokenSimilarity(tokenB, tokenA) } ?: 0.0
        }.average()

        return (simAB + simBA) / 2.0
    }

    /**
     * Tekil iki kelime için benzerlik:
     *  - 1.0 = tamamen aynı
     *  - 0.0 = tamamen farklı
     *
     * Levenshtein distance / maxLength kullanıyoruz.
     */
    private fun tokenSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val dist = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 0.0

        val sim = 1.0 - (dist.toDouble() / maxLen.toDouble())
        // Güvenlik için [0,1] aralığına kırp
        return sim.coerceIn(0.0, 1.0)
    }

    /**
     * Klasik Levenshtein mesafesi (edit distance):
     * ekleme / silme / değiştirme maliyeti = 1
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        if (len1 == 0) return len2
        if (len2 == 0) return len1

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // silme
                    dp[i][j - 1] + 1,      // ekleme
                    dp[i - 1][j - 1] + cost // değiştirme
                )
            }
        }

        return dp[len1][len2]
    }

}
