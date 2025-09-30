package com.example.b1void.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.transformer.TransformationResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.google.common.collect.ImmutableList

class VideoStampProcessor(private val context: Context) {

    suspend fun applyStamp(videoFile: File, companyLabel: String, timestampText: String): Boolean = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val overlayBitmap = createStampBitmap(width, height, companyLabel, timestampText)

            val overlaySettings = OverlaySettings.Builder()
                .setOverlayFrameAnchor(1f, 1f)
                .setBackgroundFrameAnchor(1f, 1f)
                .build()

            val overlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap, overlaySettings)
            val overlayEffect = OverlayEffect(ImmutableList.of(overlay))

            val outputFile = File(videoFile.parentFile, "${videoFile.nameWithoutExtension}_stamped.mp4")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val completion = CompletableDeferred<Boolean>()
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onTransformationCompleted(mediaItem: MediaItem, transformationResult: TransformationResult) {
                        completion.complete(true)
                    }

                    override fun onTransformationError(mediaItem: MediaItem, exception: Exception) {
                        Log.e(TAG, "Video stamp transformation failed", exception)
                        completion.complete(false)
                    }
                })
                .setVideoEffects(listOf(overlayEffect))
                .build()

            transformer.start(MediaItem.fromUri(Uri.fromFile(videoFile)), outputFile.absolutePath)

            val success = completion.await()

            overlayBitmap.recycle()

            if (success) {
                if (!videoFile.delete()) {
                    Log.w(TAG, "Failed to delete original video before renaming")
                }
                if (!outputFile.renameTo(videoFile)) {
                    Log.w(TAG, "Failed to rename stamped video to original name")
                }
            } else {
                outputFile.delete()
            }

            return@withContext success
        } finally {
            retriever.release()
        }
    }

    private fun createStampBitmap(videoWidth: Int, videoHeight: Int, company: String, timestamp: String): Bitmap {
        val bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            textAlign = Paint.Align.RIGHT
            textSize = videoWidth / 35f
        }

        val padding = videoWidth / 45f
        val fontMetrics = paint.fontMetrics
        val timestampY = videoHeight - padding - fontMetrics.bottom
        val companyY = timestampY - paint.textSize - padding * 0.3f

        val x = videoWidth - padding

        canvas.drawText(company, x, companyY, paint)
        canvas.drawText(timestamp, x, timestampY, paint)

        return bitmap
    }

    companion object {
        private const val TAG = "VideoStampProcessor"
    }
}
