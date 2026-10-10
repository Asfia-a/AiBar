package com.tavern.entity;

import lombok.Data;

@Data
public class CharacterTag {
    private Long id;
    private Long characterId;
    private Long tagId;
}
