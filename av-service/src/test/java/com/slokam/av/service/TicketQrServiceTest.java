package com.slokam.av.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TicketQrServiceTest {
    @Test
    void encodesVersionedTicketPayloadInPng() throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(new TicketQrService().image("A".repeat(32))));
        var bitmap =
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));

        assertEquals("AVT1:" + "A".repeat(32), new MultiFormatReader().decode(bitmap).getText());
    }
}
