// src/main/java/com/wino/academyapi/domain/admin/dto/AdminSignupRequest.java
package com.wino.academyapi.domain.admin.dto;

import com.wino.academyapi.domain.admin.entity.EmployeeType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AdminSignupRequest {

    @NotBlank @Size(min=3, max=50)
    private String userId;

    @NotBlank @Size(min=2, max=50)
    private String userName;

    @NotBlank @Size(min=8, max=128)
    private String password;

    @NotNull
    private EmployeeType employeeType; // STAFF | TEACHER

    @Email @Size(max=120)
    private String email;

    @Size(max=20)
    private String phoneNumber;

    @Size(max=20)
    private String emergencyContact;

    @Size(max=10)
    private String postalCode;

    @Size(max=255)
    private String address;

    @Size(max=255)
    private String detailAddress;
}
