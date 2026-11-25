package com.app.inktwin.data.room.converters

import androidx.room.TypeConverter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

class BitmapConverter {

    @TypeConverter
    fun fromBlob(blob: ByteArray?): Bitmap? {
        return blob?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    @TypeConverter
    fun toBlob(bitmap: Bitmap?): ByteArray? {
        return bitmap?.let { bmp ->
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

    // Alternative: Base64 string for easier debugging
    @TypeConverter
    fun fromBase64(base64: String?): Bitmap? {
        return base64?.let {
            val decoded = Base64.decode(it, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        }
    }

    @TypeConverter
    fun toBase64(bitmap: Bitmap?): String? {
        return bitmap?.let { bmp ->
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
        }
    }
}