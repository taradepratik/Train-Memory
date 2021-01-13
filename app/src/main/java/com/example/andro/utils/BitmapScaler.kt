package com.example.andro.utils

import android.graphics.Bitmap

object BitmapScaler {
    fun scalerToFitWidth(b: Bitmap, width: Int): Bitmap{
        val factor = width/b.width.toFloat()
        return Bitmap.createScaledBitmap(b, width, (b.height * factor).toInt(), true)
    }

    fun scalerToFitHeight(b: Bitmap, height: Int): Bitmap{
        val factor = height/b.height.toFloat()
        return Bitmap.createScaledBitmap(b, (b.width * factor).toInt(), height,true)
    }

}
