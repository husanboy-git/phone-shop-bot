package com.test.phone.service;

import com.test.phone.model.Role;
import com.test.phone.model.dto.UserDto;
import com.test.phone.model.entity.UserEntity;
import com.test.phone.repository.UserRepository;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getUserByTelegramId_shouldReturnUserDto_whenUserExists() {
        Long telegramId = 123L;
        UserEntity userEntity = new UserEntity();
        userEntity.setTelegramId(telegramId);
        userEntity.setName("Test User");
        userEntity.setRole(Role.USER);

        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(userEntity));

        Optional<UserDto> result = userService.getUserByTelegramId(telegramId);

        assertTrue(result.isPresent());
        assertEquals("Test User", result.get().name());
        assertEquals(Role.USER, result.get().role());
        verify(userRepository, times(1)).findByTelegramId(telegramId);
    }

    @Test
    void getUserByTelegramId_shouldReturnEmpty_whenUserDoesNotExist() {
        Long telegramId = 123L;
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.empty());

        Optional<UserDto> result = userService.getUserByTelegramId(telegramId);

        assertFalse(result.isPresent());
        verify(userRepository, times(1)).findByTelegramId(telegramId);
    }

    @Test
    void addUser_shouldAddUserAndReturnUserDto_whenSuccessful() {
        Long telegramId = 123L;
        String name = "Test User";
        Role role = Role.USER;
        UserEntity userEntity = UserEntity.of(telegramId, name, role);

        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        UserDto result = userService.addUser(telegramId, name, role);

        assertNotNull(result);
        assertEquals(name, result.name());
        assertEquals(role, result.role());
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    void addUser_shouldThrowException_whenUserAlreadyExists() {
        Long telegramId = 123L;
        String name = "Test User";
        Role role = Role.USER;

        when(userRepository.save(any(UserEntity.class))).thenThrow(DataIntegrityViolationException.class);

        assertThrows(IllegalArgumentException.class, () -> userService.addUser(telegramId, name, role));
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    void addAdmin_shouldPromoteUserToAdmin_whenUserExistsAsUser() {
        Long telegramId = 123L;
        String name = "Test User";
        UserEntity userEntity = UserEntity.of(telegramId, name, Role.USER);

        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(userEntity));
        when(userRepository.save(userEntity)).thenReturn(userEntity);

        UserDto result = userService.addAdmin(telegramId, name);

        assertEquals(Role.ADMIN, result.role());
        verify(userRepository, times(1)).findByTelegramId(telegramId);
        verify(userRepository, times(1)).save(userEntity);
    }

    @Test
    void addAdmin_shouldThrowException_whenUserAlreadyAdmin() {
        Long telegramId = 123L;
        String name = "Test Admin";
        UserEntity userEntity = UserEntity.of(telegramId, name, Role.ADMIN);

        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(userEntity));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.addAdmin(telegramId, name));
        assertEquals("siz admin bo'lib bo'lgansiz!!", exception.getMessage());
        verify(userRepository, times(1)).findByTelegramId(telegramId);
        verify(userRepository, never()).save(userEntity);
    }

    @Test
    void addAdmin_shouldAddNewAdmin_whenUserDoesNotExist() {
        Long telegramId = 123L;
        String name = "New Admin";
        UserEntity userEntity = UserEntity.of(telegramId, name, Role.ADMIN);

        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        UserDto result = userService.addAdmin(telegramId, name);

        assertEquals(Role.ADMIN, result.role());
        verify(userRepository, times(1)).findByTelegramId(telegramId);
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    void removeAdmin_shouldDemoteAdminToUser_whenAdminExists() {
        Long telegramId = 123L;
        UserEntity userEntity = UserEntity.of(telegramId, "Admin User", Role.ADMIN);

        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(userEntity));
        when(userRepository.save(userEntity)).thenReturn(userEntity);

        UserDto result = userService.removeAdmin(telegramId);

        assertEquals(Role.USER, result.role());
        verify(userRepository, times(1)).findByTelegramId(telegramId);
        verify(userRepository, times(1)).save(userEntity);
    }

    @Test
    void removeAdmin_shouldThrowException_whenUserIsNotAdmin() {
        Long telegramId = 123L;
        UserEntity userEntity = UserEntity.of(telegramId, "Test User", Role.USER);

        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(userEntity));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.removeAdmin(telegramId));
        assertEquals("This user is not an admin.", exception.getMessage());
        verify(userRepository, times(1)).findByTelegramId(telegramId);
        verify(userRepository, never()).save(userEntity);
    }

    @Test
    void removeAdmin_shouldThrowNotFoundException_whenUserDoesNotExist() {
        Long telegramId = 123L;

        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.removeAdmin(telegramId));
        verify(userRepository, times(1)).findByTelegramId(telegramId);
    }
}
