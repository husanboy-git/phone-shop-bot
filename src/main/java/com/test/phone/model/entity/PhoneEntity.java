package com.test.phone.model.entity;

import com.test.phone.model.dto.PhoneDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "phones")
public class PhoneEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String brand;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false)
    private String image;

    @Column(nullable = false, columnDefinition = "DECIMAL(10, 1)")
    private double price;

    @Column(name = "`condition`", length = 20, nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'used'")
    private String condition;

    public static PhoneEntity of(String brand, String model, String image, double price, String condition) {
        PhoneEntity phoneEntity = new PhoneEntity();
        phoneEntity.setBrand(brand);
        phoneEntity.setModel(model);
        phoneEntity.setImage(image);
        phoneEntity.setCondition(condition);
        phoneEntity.setPrice(price);
        return phoneEntity;
    }

    public static PhoneEntity fromDto(PhoneDto phoneDto) {
        return new PhoneEntity(
                phoneDto.id(),
                phoneDto.brand(),
                phoneDto.model(),
                phoneDto.image(),
                phoneDto.price(),
                phoneDto.condition()
        );
    }

}
