package com.example.paintquality.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record EmailProfileRequest(@Email @Size(max = 128) String email) {
}
