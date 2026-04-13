package com.eduassist.eduassist.controller;

import com.eduassist.eduassist.dto.UpdateQuestionRequest;
import com.eduassist.eduassist.entity.Material;
import com.eduassist.eduassist.entity.Quiz;
import com.eduassist.eduassist.repository.MaterialRepository;
import com.eduassist.eduassist.service.QuizGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/materials")
public class QuizController {

    private final QuizGenerationService quizGenerationService;
    private final MaterialRepository materialRepository;

    public QuizController(QuizGenerationService quizGenerationService,
                          MaterialRepository materialRepository) {
        this.quizGenerationService = quizGenerationService;
        this.materialRepository = materialRepository;
    }

    @PostMapping("/{materialId}/generate-quiz")
    public ResponseEntity<?> generateQuiz(
            @PathVariable UUID materialId,
            @RequestBody(required = false) Map<String, Object> options,
            Authentication auth
    ) {
        try {
            Material material = materialRepository.findById(materialId)
                    .orElseThrow(() -> new RuntimeException("Material not found"));

            Map<String, Object> result =
                    quizGenerationService.generateQuizPublic(material, options, auth);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(400)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportQuiz(Authentication auth) {
        try {
            Quiz quiz = quizGenerationService.exportQuiz(auth);

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Quiz exported successfully",
                            "quizId", quiz.getQuizId(),
                            "status", quiz.getStatus()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.status(400)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/questions/{questionId}/refine")
    public ResponseEntity<?> refineQuestion(
            @PathVariable UUID questionId,
            Authentication auth
    ) {
        try {
            Map<String, Object> refinedQuestion =
                    quizGenerationService.refineQuestion(questionId, auth);

            return ResponseEntity.ok(refinedQuestion);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<?> updateQuestion(
            @PathVariable UUID questionId,
            @RequestBody UpdateQuestionRequest request,
            Authentication auth
    ) {
        try {
            Map<String, Object> updatedQuestion =
                    quizGenerationService.updateQuestion(questionId, request, auth);

            return ResponseEntity.ok(updatedQuestion);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}