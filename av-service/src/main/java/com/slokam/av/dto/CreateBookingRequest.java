package com.slokam.av.dto;

import jakarta.validation.constraints.*;

import java.util.*;

public class CreateBookingRequest {
    @NotBlank public String showId;
    @NotEmpty public List<String> seatIds;
    public String alternativeMobile;

    @AssertTrue(message = "Terms and conditions must be accepted")
    public boolean termsAccepted;
}
