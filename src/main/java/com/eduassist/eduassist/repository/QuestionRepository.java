package com.eduassist.eduassist.repository;

import com.eduassist.eduassist.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByQuiz_QuizId(UUID quizId);
}

