package com.test.phone.repository;

import com.test.phone.model.entity.PhoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhoneRepository extends JpaRepository<PhoneEntity, Long> {
    List<PhoneEntity> findByBrand(String brand);
    List<PhoneEntity> findByModel(String model);
    // PhoneRepository.java
    Optional<PhoneEntity> findTopByOrderByIdDesc();

    PhoneEntity deleteByModel(String model);

}
