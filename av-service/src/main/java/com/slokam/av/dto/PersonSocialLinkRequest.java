package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class PersonSocialLinkRequest {
    @NotNull
    public SocialPlatform platform;
    @NotBlank @HttpUrl @Size(max = 2000)
    public String profileUrl;
    @Size(max = 150)
    public String username;

    public boolean isVerifiedOfficial;
}
