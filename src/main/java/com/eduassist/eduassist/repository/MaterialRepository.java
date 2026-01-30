package com.eduassist.eduassist.repository;

import com.eduassist.eduassist.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findByUser_UserId(UUID userId);
}

