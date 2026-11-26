package com.example.paintquality.user;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(@Size(max = 80) String inspectorName,
                                @Size(max = 50) String area,
                                @Size(max = 50) String checkArea,
                                Role role,
                                Boolean enabled,
                                @Size(max = 128) String email) {
}
