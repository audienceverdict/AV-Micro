package com.slokam.av.dto;

import com.slokam.av.entity.*;
import com.slokam.av.validation.HttpUrl;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public class MediaOrderRequest {
    @NotNull @Size(max = 1000)
    public List<@NotBlank String> mediaIds;
}
