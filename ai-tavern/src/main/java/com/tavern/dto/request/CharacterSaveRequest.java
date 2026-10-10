package com.tavern.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CharacterSaveRequest {
    @NotBlank
    private String name;
    private String avatar;
    private String cover;
    private String description;
    private String personality;
    private String background;
    private String exampleDialogue;
    private String greeting;
    private Long categoryId;
    private Integer isPublic;
    private List<Long> tagIds;
}
