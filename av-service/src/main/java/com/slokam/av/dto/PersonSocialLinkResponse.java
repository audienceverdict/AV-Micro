package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class PersonSocialLinkResponse {

    public SocialPlatform platform;

    public String profileUrl;

    public String username;

    public boolean isVerifiedOfficial;

    public String id;

    public String personId;
}
