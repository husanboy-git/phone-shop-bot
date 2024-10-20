package com.test.phone.model.entity;

import com.test.phone.model.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long telegramId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    public static UserEntity of(Long telegramId, String name, Role role) {
        UserEntity userEntity = new UserEntity();
        userEntity.setTelegramId(telegramId);
        userEntity.setName(name);
        userEntity.setRole(role);
        return userEntity;
    }
}
