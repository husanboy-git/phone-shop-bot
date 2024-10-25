package com.test.phone.service;

import com.test.phone.model.dto.PhoneDto;
import com.test.phone.model.entity.PhoneEntity;
import com.test.phone.repository.PhoneRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

@Service
public class PhoneService {
    @Autowired private PhoneRepository phoneRepository;

    public List<PhoneDto> getPhonesByBrand(String brand) {
        List<PhoneEntity> byBrand = phoneRepository.findByBrand(brand);
        return byBrand.stream().map(PhoneDto::toDto).toList();
    }

    public List<PhoneDto> getPhonesByModel(String model) {
        List<PhoneEntity> byModel = phoneRepository.findByModel(model);
        return byModel.stream().map(PhoneDto::toDto).toList();
    }

    @Transactional
    public PhoneDto addPhone(PhoneDto phoneDto, File imageFile) throws IOException {
        if (!ImageUtils.isValidImageFile(imageFile)) {
            throw new IllegalArgumentException("Invalid image file.");
        }

        String imagePath = "images/" + phoneDto.model() + "_" + System.currentTimeMillis() + ".png";
        File destinationFile = new File(imagePath);

        try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
            fos.write(Files.readAllBytes(imageFile.toPath()));
        }

        // PhoneEntity를 생성하고 이미지 경로 설정
        PhoneEntity entity = PhoneEntity.of(
                phoneDto.brand(),
                phoneDto.model(),
                phoneDto.price(),
                phoneDto.imagePath(),
                phoneDto.condition());
        entity.setImage(imagePath); // 이미지 경로 설정 추가
        PhoneEntity savedEntity = phoneRepository.save(entity);
        return PhoneDto.toDto(savedEntity);
    }



    @Transactional
    public PhoneDto updatePhone(Long id, PhoneDto phoneDto) {
        PhoneEntity entity = phoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Phone not found"));
        entity.setBrand(phoneDto.brand());
        entity.setModel(phoneDto.model());
        entity.setPrice(phoneDto.price());
        entity.setImage(phoneDto.imagePath());
        entity.setCondition(phoneDto.condition());
        phoneRepository.save(entity);
        return PhoneDto.toDto(entity);
    }

    @Transactional
    public void deletePhone(Long id) {
        PhoneEntity phoneEntity = phoneRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Phone not found"));
        phoneRepository.delete(phoneEntity);
    }

    public Optional<PhoneDto> getPhoneById(Long id) {
        PhoneEntity phoneEntity = phoneRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("phone is not found!"));
        return Optional.of(PhoneDto.toDto(phoneEntity));
    }
}
