package com.oms.profile.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String fullname;
    private String phone;
    private String avatarUrl;
    private String gender; 
    private LocalDate dateOfBirth;
}
