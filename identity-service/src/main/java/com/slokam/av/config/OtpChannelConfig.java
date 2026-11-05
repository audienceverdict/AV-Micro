package com.slokam.av.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OtpChannelConfig {
    public enum Channel { WHATSAPP, EMAIL }
    private final Channel channel;
    public OtpChannelConfig(@Value("${auth.otp.channel:WHATSAPP}") String channel) {
        this.channel = Channel.valueOf(channel.trim().toUpperCase(java.util.Locale.ROOT));
    }
    public Channel channel() { return channel; }
    public void requireEmail() {
        throw new com.slokam.av.exception.custom.ApiException(
                409, "OTP_CHANNEL_DISABLED", "Legacy OTP endpoints are disabled; use /api/v1/auth/otp login and registration endpoints");
    }
}
