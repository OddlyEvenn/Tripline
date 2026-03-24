package com.tripline.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Component
public class QrCodeUtils {

    /**
     * Generates a Base64-encoded PNG QR code string for the given content.
     *
     * @param content The text to encode (e.g., ticketId + bookingId +
     *                passengerName)
     * @param width   QR image width in pixels
     * @param height  QR image height in pixels
     * @return Base64 PNG string, suitable for embedding in HTML or storing in DB
     */
    public String generateQrCodeBase64(String content, int width, int height)
            throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        byte[] qrBytes = outputStream.toByteArray();
        return Base64.getEncoder().encodeToString(qrBytes);
    }

    /**
     * Generates a 300×300 QR code as a Base64 PNG string.
     */
    public String generateQrCodeBase64(String content) throws WriterException, IOException {
        return generateQrCodeBase64(content, 300, 300);
    }
}
