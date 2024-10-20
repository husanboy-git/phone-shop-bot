package com.test.phone.model.dto;

import com.test.phone.model.entity.PhoneEntity;

public record PhoneDto(
        Long id,
        String brand,
        String model,
        String image,
        double price,
        String condition
) {
    public static PhoneDto toDto(PhoneEntity phoneEntity) {
        return new PhoneDto(
                phoneEntity.getId(),
                phoneEntity.getBrand(),
                phoneEntity.getModel(),
                phoneEntity.getImage(),
                phoneEntity.getPrice(),
                phoneEntity.getCondition()
        );
    }
}
