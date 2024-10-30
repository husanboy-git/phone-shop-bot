package com.test.phone.service;

import com.test.phone.model.Role;
import com.test.phone.model.dto.UserDto;
import com.test.phone.model.entity.UserEntity;
import com.test.phone.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    @Autowired private UserRepository userRepository;

    public Optional<UserDto> getUserByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).map(UserDto::toDto);
    }

    @Transactional
    public UserDto addUser(Long telegramId, String name, Role role) {
        try {
            UserEntity savedEntity = userRepository.save(UserEntity.of(telegramId, name, role));
            return UserDto.toDto(savedEntity);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("User with this telegram Id already exists!", e);
        }
    }

    @Transactional
    public  UserDto addAdmin(Long telegramId, String name) {
        Optional<UserEntity> existingUser = userRepository.findByTelegramId(telegramId);
        if(existingUser.isPresent()) {
            UserEntity user = existingUser.get();
            if(user.getRole() == Role.ADMIN) {
                throw new IllegalArgumentException("siz admin bo'lib bo'lgansiz!!");
            }
            user.setRole(Role.ADMIN);      // 역할을 ADMIN으로 변경
            userRepository.save(user);
            return UserDto.toDto(user);
        } else {
            // 새로운 관리자를 추가
            return addUser(telegramId, name, Role.ADMIN);
        }
    }

    @Transactional
    public  UserDto removeAdmin(Long telegramId) {
        Optional<UserEntity> existingUser = userRepository.findByTelegramId(telegramId);
        if(existingUser.isPresent()) {
            UserEntity user = existingUser.get();
            if(user.getRole() != Role.ADMIN) {
                throw new IllegalArgumentException("This user is not an admin.");
            }
            user.setRole(Role.USER);      // 역할을 User으로 변경
            userRepository.save(user);
            return UserDto.toDto(user);
        } else {
            throw new NotFoundException("User not found");
        }
    }
}
