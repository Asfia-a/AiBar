package com.tavern.dto.request;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
}
