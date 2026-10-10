package com.tavern.dto.response;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private UserProfileResponse user;
}
