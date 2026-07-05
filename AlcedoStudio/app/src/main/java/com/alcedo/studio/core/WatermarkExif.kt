package com.alcedo.studio.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import com.alcedo.studio.data.model.ExifData
import java.io.OutputStream

class WatermarkRenderer(private val context: Context) {

    fun renderTextWatermark(
        bitmap: Bitmap,
        text: String,
        position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
        opacity: Float = 0.5f,
        fontSize: Float = 24f,
        padding: Int = 20,
        color: Int = Color.WHITE
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val paint = Paint().apply {
            this.color = color
            this.alpha = (opacity * 255).toInt()
            this.textSize = fontSize * context.resources.displayMetrics.scaledDensity
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            this.isAntiAlias = true
            this.textAlign = Paint.Align.LEFT
        }

        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)

        val (x, y) = when (position) {
            WatermarkPosition.TOP_LEFT -> padding.toFloat() to (padding + textBounds.height()).toFloat()
            WatermarkPosition.TOP_RIGHT -> (bitmap.width - textBounds.width() - padding).toFloat() to
                (padding + textBounds.height()).toFloat()
            WatermarkPosition.TOP_CENTER -> ((bitmap.width - textBounds.width()) / 2f) to
                (padding + textBounds.height()).toFloat()
            WatermarkPosition.BOTTOM_LEFT -> padding.toFloat() to
                (bitmap.height - padding).toFloat()
            WatermarkPosition.BOTTOM_RIGHT -> (bitmap.width - textBounds.width() - padding).toFloat() to
                (bitmap.height - padding).toFloat()
            WatermarkPosition.BOTTOM_CENTER -> ((bitmap.width - textBounds.width()) / 2f) to
                (bitmap.height - padding).toFloat()
            WatermarkPosition.CENTER -> ((bitmap.width - textBounds.width()) / 2f) to
                ((bitmap.height + textBounds.height()) / 2f)
        }

        paint.setShadowLayer(2f, 1f, 1f, Color.BLACK)
        canvas.drawText(text, x, y, paint)

        return result
    }

    fun renderImageWatermark(
        bitmap: Bitmap,
        watermarkBitmap: Bitmap,
        position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
        opacity: Float = 0.5f,
        scale: Float = 0.2f,
        padding: Int = 20
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (watermarkBitmap.height * scaledWidth / watermarkBitmap.width).coerceAtMost(
            (bitmap.height * scale).toInt()
        )

        val scaledWatermark = Bitmap.createScaledBitmap(watermarkBitmap, scaledWidth, scaledHeight, true)

        val (x, y) = when (position) {
            WatermarkPosition.TOP_LEFT -> padding to padding
            WatermarkPosition.TOP_RIGHT -> bitmap.width - scaledWidth - padding to padding
            WatermarkPosition.TOP_CENTER -> (bitmap.width - scaledWidth) / 2 to padding
            WatermarkPosition.BOTTOM_LEFT -> padding to bitmap.height - scaledHeight - padding
            WatermarkPosition.BOTTOM_RIGHT -> bitmap.width - scaledWidth - padding to
                bitmap.height - scaledHeight - padding
            WatermarkPosition.BOTTOM_CENTER -> (bitmap.width - scaledWidth) / 2 to
                bitmap.height - scaledHeight - padding
            WatermarkPosition.CENTER -> (bitmap.width - scaledWidth) / 2 to
                (bitmap.height - scaledHeight) / 2
        }

        val paint = Paint().apply {
            alpha = (opacity * 255).toInt()
            isAntiAlias = true
        }

        canvas.drawBitmap(scaledWatermark, x.toFloat(), y.toFloat(), paint)

        if (scaledWatermark != watermarkBitmap) {
            scaledWatermark.recycle()
        }

        return result
    }
}

enum class WatermarkPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    CENTER
}

class ExifWriter(private val context: Context) {

    fun writeExif(
        outputStream: OutputStream,
        exifData: ExifData,
        includeMetadata: Boolean = true,
        includeGps: Boolean = true
    ) {
        try {
            val exif = ExifInterface(outputStream)

            if (includeMetadata) {
                exif.setAttribute(ExifInterface.TAG_MAKE, exifData.cameraMake)
                exif.setAttribute(ExifInterface.TAG_MODEL, exifData.cameraModel)
                exif.setAttribute(ExifInterface.TAG_ARTIST, exifData.artist)
                exif.setAttribute(ExifInterface.TAG_COPYRIGHT, exifData.copyright)
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, exifData.description)

                if (exifData.iso > 0) {
                    exif.setAttribute(ExifInterface.TAG_ISO_SPEED, exifData.iso.toString())
                }

                if (exifData.aperture > 0f) {
                    exif.setAttribute(
                        ExifInterface.TAG_APERTURE_VALUE,
                        exifData.aperture.toString()
                    )
                }

                if (exifData.shutterSpeed.isNotEmpty()) {
                    exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, exifData.shutterSpeed)
                }

                if (exifData.focalLength > 0f) {
                    exif.setAttribute(
                        ExifInterface.TAG_FOCAL_LENGTH,
                        "${exifData.focalLength}/1"
                    )
                }
            }

            if (includeGps && exifData.gpsLatitude != null && exifData.gpsLongitude != null) {
                val lat = exifData.gpsLatitude!!
                val lng = exifData.gpsLongitude!!

                val latRef = if (lat >= 0) "N" else "S"
                val lngRef = if (lng >= 0) "E" else "W"

                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lngRef)
                exif.setAttribute(
                    ExifInterface.TAG_GPS_LATITUDE,
                    convertToDMS(kotlin.math.abs(lat))
                )
                exif.setAttribute(
                    ExifInterface.TAG_GPS_LONGITUDE,
                    convertToDMS(kotlin.math.abs(lng))
                )

                if (exifData.gpsAltitude != null) {
                    exif.setAttribute(
                        ExifInterface.TAG_GPS_ALTITUDE,
                        "${exifData.gpsAltitude!!.toInt()}/1"
                    )
                }
            }

            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun convertToDMS(decimal: Double): String {
        val degrees = decimal.toInt()
        val minutesDecimal = (decimal - degrees) * 60
        val minutes = minutesDecimal.toInt()
        val seconds = (minutesDecimal - minutes) * 60 * 10000

        return "$degrees/1,$minutes/1,${seconds.toInt()}/10000"
    }

    fun setOrientation(outputStream: OutputStream, orientation: Int) {
        try {
            val exif = ExifInterface(outputStream)
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                orientation.toString()
            )
            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSoftware(outputStream: OutputStream, software: String) {
        try {
            val exif = ExifInterface(outputStream)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, software)
            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
