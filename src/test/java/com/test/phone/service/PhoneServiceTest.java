package com.test.phone.service;

import com.test.phone.model.dto.PhoneDto;
import com.test.phone.model.entity.PhoneEntity;
import com.test.phone.repository.PhoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PhoneServiceTest {

    @Mock private PhoneRepository phoneRepository;
    @Mock private ImageUtils imageUtils;

    @InjectMocks private PhoneService phoneService;

    private PhoneDto phoneDto;
    private PhoneEntity phoneEntity;
    private File imageFile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        phoneDto = new PhoneDto(1L, "Samsung", "Galaxy S21", 1200.00, "images/sample.png", "new");
        phoneEntity = new PhoneEntity(2L, "Samsung", "Galaxy S21", 1200.00, "images/sample.png", "new");
        imageFile = mock(File.class);
    }

    @Test
    public void testAddPhone_Success() throws IOException {
        // 이미지 파일의 이름을 반환하도록 설정합니다.
        when(imageFile.getName()).thenReturn("sample.png");
        when(imageUtils.isValidImageFile(any())).thenReturn(true); // 이미지 검증을 항상 통과하도록 설정
        when(phoneRepository.save(any(PhoneEntity.class))).thenReturn(phoneEntity);

        PhoneDto result = phoneService.addPhone(phoneDto, imageFile);

        assertNotNull(result);
        assertEquals(phoneDto.model(), result.model());
    }

    @Test
    void testUpdatePhone_Success() {
        // Arrange
        PhoneEntity phoneToUpdate = new PhoneEntity();
        // Set necessary fields and mock behavior

        // Ensure `imageFile` is not null
        File validImageFile = new File("path/to/valid/image.jpg"); // Ensure this file exists
        when(imageUtils.isValidImageFile(validImageFile)).thenReturn(true);

        // Act
        phoneService.updatePhone(phoneToUpdate, validImageFile); // Pass valid image

        // Assert
        // Add assertions
    }


    @Test
    public void testUpdatePhone_NotFound() {
        when(phoneRepository.findById(anyLong())).thenReturn(Optional.empty());

        // IllegalArgumentException 예외를 예상합니다.
        assertThrows(IllegalArgumentException.class, () -> {
            phoneService.updatePhone(1L, phoneDto, imageFile);
        });
    }

    public static boolean isValidImageFile(File imageFile) {
        if (imageFile == null) {
            return false; // or throw an appropriate exception
        }
        // existing code...
        return true;
    }


}
