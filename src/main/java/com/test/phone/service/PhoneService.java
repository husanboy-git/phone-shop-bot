package com.test.phone.service;

import com.test.phone.model.dto.PhoneDto;
import com.test.phone.model.entity.PhoneEntity;
import com.test.phone.repository.PhoneRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PhoneService {
    @Autowired private PhoneRepository phoneRepository;

    public List<PhoneDto> getAllPhones() {
        List<PhoneEntity> entities = phoneRepository.findAll();
        return entities.stream().map(PhoneDto::toDto).toList();
    }

    public List<PhoneDto> getPhonesByBrand(String brand) {
        List<PhoneEntity> byBrand = phoneRepository.findByBrand(brand);
        return byBrand.stream().map(PhoneDto::toDto).toList();
    }

    public List<PhoneDto> getPhonesByModel(String model) {
        List<PhoneEntity> byModel = phoneRepository.findByModel(model);
        return byModel.stream().map(PhoneDto::toDto).toList();
    }

    @Transactional
    public PhoneDto addPhone(PhoneDto phoneDto) {
        PhoneEntity entity = PhoneEntity.fromDto(phoneDto);
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
        entity.setImage(phoneDto.image());
        entity.setCondition(phoneDto.condition());
        phoneRepository.save(entity);
        return PhoneDto.toDto(entity);
    }

    @Transactional
    public void deletePhone(Long id) {
        phoneRepository.deleteById(id);
    }
}
