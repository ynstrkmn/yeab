package com.yeab.esnapp.util

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Size
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

object ImageSimilarityUtils {

    /**
     * İki Bitmap arasındaki benzerliği 0.0 - 1.0 arası skor olarak döner.
     * 1.0 -> birebir aynı / çok yüksek benzerlik
     * 0.0 -> benzerlik yok
     *
     * DİKKAT: Bu method UI thread dışında (background thread) çağrılmalı.
     */
    @JvmStatic
    fun calculateSimilarity(bitmap1: Bitmap, bitmap2: Bitmap): Double {
        try {
            // 1) Performans için bitmap’leri küçült (max 600px genişlik/yükseklik)
            val scaled1 = resizeBitmap(bitmap1, 600)
            val scaled2 = resizeBitmap(bitmap2, 600)

            // 2) Bitmap -> Mat
            val mat1 = Mat()
            val mat2 = Mat()
            Utils.bitmapToMat(scaled1, mat1)
            Utils.bitmapToMat(scaled2, mat2)

            // 3) Gri tonlamaya çevir
            val gray1 = Mat()
            val gray2 = Mat()
            Imgproc.cvtColor(mat1, gray1, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(mat2, gray2, Imgproc.COLOR_RGBA2GRAY)

            // 4) ORB keypoint + descriptor çıkar
            val orb = ORB.create(
                1000,           // maxFeatures
                1.2f,          // scaleFactor
                8,             // nLevels
                31,            // edgeThreshold
                0,             // firstLevel
                2,             // WTA_K
                ORB.HARRIS_SCORE,
                31,            // patchSize
                20             // fastThreshold
            )

            val keypoints1 = MatOfKeyPoint()
            val descriptors1 = Mat()
            val keypoints2 = MatOfKeyPoint()
            val descriptors2 = Mat()

            orb.detectAndCompute(gray1, Mat(), keypoints1, descriptors1)
            orb.detectAndCompute(gray2, Mat(), keypoints2, descriptors2)

            // Keypoint yoksa benzerlik 0
            if (descriptors1.empty() || descriptors2.empty()) {
                return 0.0
            }

            // 5) BFMatcher + kNN (ratio test için k=2)
            val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
            val knnMatches = ArrayList<MatOfDMatch>()
            matcher.knnMatch(descriptors1, descriptors2, knnMatches, 2)

            // 6) Lowe ratio test ile "iyi eşleşmeleri" say
            val goodMatches = ArrayList<org.opencv.core.DMatch>()
            val ratioThresh = 0.75f
            val maxDist = 60f
            for (matOfDMatch in knnMatches) {
                val matches = matOfDMatch.toArray()
                if (matches.size >= 2) {
                    val m1 = matches[0]
                    val m2 = matches[1]
                    if (m1.distance < ratioThresh * m2.distance && m1.distance < maxDist) {
                        goodMatches.add(m1)
                    }
                }
            }

            val minKeypoints = minOf(keypoints1.toArray().size, keypoints2.toArray().size)
            if (minKeypoints == 0) return 0.0

            // 7) Benzerlik skoru: iyi eşleşme oranı
            val score = goodMatches.size.toDouble() / minKeypoints.toDouble()

            // Clamp 0.0 - 1.0 arasına
            return score.coerceIn(0.0, 1.0)

        } catch (e: Exception) {
            // Hata durumunda benzerlik 0 say
            return 0.0
        }
    }

    /**
     * Bitmap'i orijinal oranı bozmadan maxSize (genişlik veya yükseklik) olacak şekilde küçültür.
     */
    private fun resizeBitmap(src: Bitmap, maxSize: Int): Bitmap {
        val width = src.width
        val height = src.height
        if (width <= maxSize && height <= maxSize) {
            return src
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (ratio > 1f) {
            // yatay
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            // dikey
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(src, newWidth, newHeight, true)
    }
}
