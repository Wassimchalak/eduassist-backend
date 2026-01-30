package com.eduassist.eduassist.repository;

import com.eduassist.eduassist.entity.OptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OptionItemRepository extends JpaRepository<OptionItem, UUID> {

    List<OptionItem> findByQuestion_QuestionId(UUID questionId);
}

