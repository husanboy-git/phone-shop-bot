package com.test.phone.model.dto;

import com.test.phone.model.Role;
import com.test.phone.model.entity.UserEntity;

public record UserDto(
        Long id,
        Long telegramId,
        String name,
        Role role
) {
    public static UserDto toDto(UserEntity userEntity) {
        return new UserDto(userEntity.getId(),
                userEntity.getTelegramId(),
                userEntity.getName(),
                userEntity.getRole());
    }
}
