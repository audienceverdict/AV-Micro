package com.slokam.av.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TicketQrService {
    private static final String QR_PREFIX = "AVT1:";
    private static final int QR_SIZE = 320;

    public String payload(String ticketCode) {
        return QR_PREFIX + ticketCode;
    }

    public byte[] image(String ticketCode) {
        try {
            var matrix =
                    new QRCodeWriter()
                            .encode(
                                    payload(ticketCode),
                                    BarcodeFormat.QR_CODE,
                                    QR_SIZE,
                                    QR_SIZE,
                                    Map.of(
                                            EncodeHintType.ERROR_CORRECTION,
                                            ErrorCorrectionLevel.M,
                                            EncodeHintType.MARGIN,
                                            2));
            var output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Unable to generate ticket QR code", e);
        }
    }

    public String imageDataUri(String ticketCode) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(image(ticketCode));
    }
}
