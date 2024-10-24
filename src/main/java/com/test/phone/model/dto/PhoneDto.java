package com.test.phone.model.dto;

import com.test.phone.model.entity.PhoneEntity;

public record PhoneDto(
        Long id,
        String brand,
        String model,
        double price,
        String imagePath,
        String condition
) {
    public static PhoneDto toDto(PhoneEntity phoneEntity) {
        return new PhoneDto(
                phoneEntity.getId(),
                phoneEntity.getBrand(),
                phoneEntity.getModel(),
                phoneEntity.getPrice(),
                phoneEntity.getImage(),
                phoneEntity.getCondition()
        );
    }
}
