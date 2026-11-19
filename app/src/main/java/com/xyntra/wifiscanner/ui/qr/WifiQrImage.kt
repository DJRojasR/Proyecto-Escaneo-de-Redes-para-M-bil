package com.xyntra.wifiscanner.ui.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Genera el QR (con ZXing) solo para mostrarlo en pantalla. Todo ocurre en el teléfono. */
object WifiQrImage {

    fun matrix(text: String): BitMatrix? = try {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 2,
            ),
        )
    } catch (_: Exception) {
        null
    }

    /** Un píxel por módulo: la pantalla lo agranda sin suavizar para que quede nítido. */
    fun bitmap(m: BitMatrix): Bitmap {
        val w = m.width
        val h = m.height
        val px = IntArray(w * h) { i -> if (m.get(i % w, i / w)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
