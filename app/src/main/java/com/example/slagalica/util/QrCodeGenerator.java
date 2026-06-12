package com.example.slagalica.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * Generiše QR kod (bitmap) od zadatog teksta pomoću ZXing biblioteke.
 * Koristi se na profilu za QR kod sa ID-jem korisnika (poziv za prijatelja).
 */
public final class QrCodeGenerator {

    private QrCodeGenerator() {
        // ne instancira se
    }

    /**
     * Generiše kvadratni QR kod.
     *
     * @param content tekst koji se enkodira (npr. UID korisnika)
     * @param sizePx  širina/visina bitmape u pikselima
     * @return bitmap sa QR kodom ili {@code null} ako enkodiranje ne uspe
     */
    @Nullable
    public static Bitmap generate(String content, int sizePx) {
        try {
            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx);
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException | IllegalArgumentException e) {
            return null;
        }
    }
}
