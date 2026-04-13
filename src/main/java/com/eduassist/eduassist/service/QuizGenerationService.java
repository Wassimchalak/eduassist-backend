package com.eduassist.eduassist.service;

import com.eduassist.eduassist.dto.AiQuizPayload;
import com.eduassist.eduassist.dto.UpdateQuestionRequest;
import com.eduassist.eduassist.entity.AppUser;
import com.eduassist.eduassist.entity.Material;
import com.eduassist.eduassist.entity.OptionItem;
import com.eduassist.eduassist.entity.Question;
import com.eduassist.eduassist.entity.Quiz;
import com.eduassist.eduassist.repository.OptionItemRepository;
import com.eduassist.eduassist.repository.QuestionRepository;
import com.eduassist.eduassist.repository.QuizRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QuizGenerationService {

    private final MaterialContentService materialContentService;
    private final GeminiClient geminiClient;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final OptionItemRepository optionItemRepository;
    private final CurrentUserService currentUserService;

    private final ObjectMapper mapper = new ObjectMapper();

    public QuizGenerationService(
            QuizRepository quizRepository,
            QuestionRepository questionRepository,
            OptionItemRepository optionItemRepository,
            MaterialContentService materialContentService,
            GeminiClient geminiClient,
            CurrentUserService currentUserService) {

        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.optionItemRepository = optionItemRepository;
        this.materialContentService = materialContentService;
        this.geminiClient = geminiClient;
        this.currentUserService = currentUserService;
    }

    // ================= GENERATE QUIZ =================

    @Transactional
    public Map<String, Object> generateQuizPublic(
            Material material,
            Map<String, Object> options,
            Authentication auth
    ) {

        String materialText = materialContentService.extractText(material);
        if (materialText == null) {
            materialText = "";
        }
        materialText = materialText.trim();

        if (materialText.length() > 12000) {
            materialText = materialText.substring(0, 12000);
        }

        int questionCount = 5;

        if (options != null) {
            Object value = options.get("questionCount");

            if (value == null) {
                value = options.get("numberOfQuestions");
            }

            if (value instanceof Number) {
                questionCount = ((Number) value).intValue();
            }
        }

        List<String> selectedTypes = new ArrayList<>();

        if (options != null) {
            Object typesObj = options.get("questionTypes");

            if (typesObj instanceof List<?>) {
                for (Object obj : (List<?>) typesObj) {
                    if (obj != null) {
                        selectedTypes.add(obj.toString());
                    }
                }
            }
        }

        if (selectedTypes.isEmpty()) {
            selectedTypes.add("MCQ");
        }

        String aiJson = geminiClient.generateQuizJson(
                materialText,
                questionCount,
                selectedTypes,
                false
        );

        AiQuizPayload payload = parseAiPayload(aiJson);

        if (payload.questions == null || payload.questions.isEmpty()) {
            throw new RuntimeException("AI returned no questions");
        }

        AppUser user = currentUserService.getCurrentUser(auth);

        Quiz quiz = new Quiz();
        quiz.setMaterial(material);
        quiz.setUser(user);
        quiz.setTitle("Quiz for: " + material.getTitle());
        quiz.setStatus("DRAFT");
        quiz.setReviewStatus("PENDING");
        quiz.setVersion(1);

        try {
            quiz.setQuestionsJson(mapper.writeValueAsString(payload.questions));
        } catch (Exception e) {
            throw new RuntimeException("Failed to store questions JSON", e);
        }

        quiz = quizRepository.save(quiz);

        List<Map<String, Object>> questionDtos = saveQuestions(payload, quiz);

        return Map.of(
                "quizId", quiz.getQuizId(),
                "questions", questionDtos
        );
    }

    // ================= REFINE QUESTION =================

    @Transactional
    public Map<String, Object> refineQuestion(UUID questionId, Authentication auth) {

        AppUser user = currentUserService.getCurrentUser(auth);

        Question oldQuestion = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        Quiz quiz = oldQuestion.getQuiz();

        if (quiz == null) {
            throw new RuntimeException("Quiz not found for question");
        }

        if (quiz.getUser() == null || !quiz.getUser().getUserId().equals(user.getUserId())) {
            throw new RuntimeException("You are not allowed to refine this question");
        }

        Material material = quiz.getMaterial();

        if (material == null) {
            throw new RuntimeException("Material not found for quiz");
        }

        String materialText = materialContentService.extractText(material);
        if (materialText == null) {
            materialText = "";
        }
        materialText = materialText.trim();

        if (materialText.length() > 12000) {
            materialText = materialText.substring(0, 12000);
        }

        List<String> selectedTypes = List.of(oldQuestion.getQuestionType());

        String aiJson = geminiClient.generateQuizJson(
                materialText,
                1,
                selectedTypes,
                true
        );

        AiQuizPayload payload = parseAiPayload(aiJson);

        if (payload.questions == null || payload.questions.isEmpty()) {
            throw new RuntimeException("AI returned no refined question");
        }

        AiQuizPayload.AiQuestion aq = payload.questions.get(0);

        oldQuestion.setQuestionType(
                aq.questionType == null || aq.questionType.isBlank()
                        ? oldQuestion.getQuestionType()
                        : aq.questionType
        );
        oldQuestion.setPrompt(aq.prompt);
        oldQuestion.setExplanation(aq.explanation);
        oldQuestion.setPoints(aq.points == null ? 1 : aq.points);

        oldQuestion = questionRepository.save(oldQuestion);

        List<OptionItem> oldOptions =
                optionItemRepository.findByQuestion_QuestionId(oldQuestion.getQuestionId());
        optionItemRepository.deleteAll(oldOptions);

        List<Map<String, Object>> optionDtos = new ArrayList<>();

        if (aq.options != null) {
            for (AiQuizPayload.AiOption ao : aq.options) {

                if (ao.optionText == null || ao.optionText.isBlank()) {
                    continue;
                }

                OptionItem option = new OptionItem();
                option.setQuestion(oldQuestion);
                option.setOptionText(ao.optionText);
                option.setCorrect(Boolean.TRUE.equals(ao.isCorrect));

                OptionItem savedOption = optionItemRepository.save(option);

                optionDtos.add(Map.of(
                        "optionId", savedOption.getOptionId(),
                        "optionText", savedOption.getOptionText(),
                        "correct", savedOption.getCorrect()
                ));
            }
        }

        return Map.of(
                "questionId", oldQuestion.getQuestionId(),
                "questionType", oldQuestion.getQuestionType(),
                "prompt", oldQuestion.getPrompt(),
                "explanation", oldQuestion.getExplanation(),
                "points", oldQuestion.getPoints(),
                "options", optionDtos
        );
    }

    // ================= EXPORT QUIZ =================

    @Transactional
    public Quiz exportQuiz(Authentication auth) {

        AppUser user = currentUserService.getCurrentUser(auth);

        List<Quiz> quizzes = quizRepository.findByUser_UserId(user.getUserId());

        if (quizzes.isEmpty()) {
            throw new RuntimeException("No quiz found to export");
        }

        Quiz quiz = quizzes.get(quizzes.size() - 1);

        if ("FINAL".equals(quiz.getStatus())) {
            throw new RuntimeException("Quiz already exported");
        }

        quiz.setStatus("FINAL");

        return quizRepository.save(quiz);
    }

    // ================= SAVE QUESTIONS HELPER =================

    private List<Map<String, Object>> saveQuestions(
            AiQuizPayload payload,
            Quiz quiz
    ) {

        List<Map<String, Object>> questionDtos = new ArrayList<>();

        for (AiQuizPayload.AiQuestion aq : payload.questions) {

            if (aq.prompt == null || aq.prompt.isBlank()) {
                continue;
            }

            Question question = new Question();
            question.setQuiz(quiz);
            question.setQuestionType(aq.questionType == null ? "MCQ" : aq.questionType);
            question.setPrompt(aq.prompt);
            question.setExplanation(aq.explanation);
            question.setPoints(aq.points == null ? 1 : aq.points);

            question = questionRepository.save(question);

            List<Map<String, Object>> optionDtos = new ArrayList<>();

            if (aq.options != null) {
                for (AiQuizPayload.AiOption ao : aq.options) {

                    if (ao.optionText == null || ao.optionText.isBlank()) {
                        continue;
                    }

                    OptionItem option = new OptionItem();
                    option.setQuestion(question);
                    option.setOptionText(ao.optionText);
                    option.setCorrect(Boolean.TRUE.equals(ao.isCorrect));

                    OptionItem saved = optionItemRepository.save(option);

                    optionDtos.add(Map.of(
                            "optionId", saved.getOptionId(),
                            "optionText", saved.getOptionText(),
                            "correct", saved.getCorrect()
                    ));
                }
            }

            questionDtos.add(Map.of(
                    "questionId", question.getQuestionId(),
                    "questionType", question.getQuestionType(),
                    "prompt", question.getPrompt(),
                    "explanation", question.getExplanation(),
                    "points", question.getPoints(),
                    "options", optionDtos
            ));
        }

        return questionDtos;
    }

    // ================= AI PARSE HELPER =================

    private AiQuizPayload parseAiPayload(String aiJson) {

        if (aiJson == null || aiJson.isBlank()) {
            throw new RuntimeException("AI returned empty response");
        }

        aiJson = aiJson.trim();

        int start = aiJson.indexOf('{');
        int end = aiJson.lastIndexOf('}');

        if (start == -1 || end == -1 || end <= start) {
            throw new RuntimeException("AI response does not contain valid JSON");
        }

        String cleanJson = aiJson.substring(start, end + 1);

        try {
            return mapper.readValue(cleanJson, AiQuizPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("AI JSON parsing failed", e);
        }
    }

    // ================= EDIT QUESTION =================

    @Transactional
    public Map<String, Object> updateQuestion(
            UUID questionId,
            UpdateQuestionRequest request,
            Authentication auth
    ) {
        AppUser user = currentUserService.getCurrentUser(auth);

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        Quiz quiz = question.getQuiz();

        if (quiz == null || quiz.getUser() == null ||
                !quiz.getUser().getUserId().equals(user.getUserId())) {
            throw new RuntimeException("You are not allowed to edit this question");
        }

        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new RuntimeException("Question prompt is required");
        }

        question.setPrompt(request.getPrompt().trim());
        question.setExplanation(
                request.getExplanation() == null ? null : request.getExplanation().trim()
        );
        question.setPoints(request.getPoints() == null ? 1 : request.getPoints());

        String type = question.getQuestionType() == null ? "" : question.getQuestionType().trim();

        List<UpdateQuestionRequest.UpdateOptionRequest> requestOptions =
                request.getOptions() == null ? new ArrayList<>() : request.getOptions();

        if ("MCQ".equalsIgnoreCase(type)) {
            if (requestOptions.size() != 4) {
                throw new RuntimeException("MCQ must have exactly 4 options");
            }

            int correctCount = 0;
            for (UpdateQuestionRequest.UpdateOptionRequest o : requestOptions) {
                if (o.getOptionText() == null || o.getOptionText().isBlank()) {
                    throw new RuntimeException("MCQ option text cannot be empty");
                }
                if (Boolean.TRUE.equals(o.getCorrect())) {
                    correctCount++;
                }
            }

            if (correctCount != 1) {
                throw new RuntimeException("MCQ must have exactly 1 correct option");
            }
        }

        if ("TRUE_FALSE".equalsIgnoreCase(type)) {
            if (requestOptions.size() != 2) {
                throw new RuntimeException("TRUE_FALSE must have exactly 2 options");
            }

            int correctCount = 0;
            for (UpdateQuestionRequest.UpdateOptionRequest o : requestOptions) {
                if (o.getOptionText() == null || o.getOptionText().isBlank()) {
                    throw new RuntimeException("TRUE_FALSE option text cannot be empty");
                }
                if (Boolean.TRUE.equals(o.getCorrect())) {
                    correctCount++;
                }
            }

            if (correctCount != 1) {
                throw new RuntimeException("TRUE_FALSE must have exactly 1 correct option");
            }
        }

        if ("SHORT_ANSWER".equalsIgnoreCase(type)) {
            requestOptions = new ArrayList<>();
        }

        question = questionRepository.save(question);

        List<OptionItem> oldOptions =
                optionItemRepository.findByQuestion_QuestionId(question.getQuestionId());
        optionItemRepository.deleteAll(oldOptions);

        List<Map<String, Object>> optionDtos = new ArrayList<>();

        for (UpdateQuestionRequest.UpdateOptionRequest o : requestOptions) {
            OptionItem option = new OptionItem();
            option.setQuestion(question);
            option.setOptionText(o.getOptionText().trim());
            option.setCorrect(Boolean.TRUE.equals(o.getCorrect()));

            OptionItem saved = optionItemRepository.save(option);

            optionDtos.add(Map.of(
                    "optionId", saved.getOptionId(),
                    "optionText", saved.getOptionText(),
                    "correct", saved.getCorrect()
            ));
        }

        return Map.of(
                "questionId", question.getQuestionId(),
                "questionType", question.getQuestionType(),
                "prompt", question.getPrompt(),
                "explanation", question.getExplanation(),
                "points", question.getPoints(),
                "options", optionDtos
        );
    }
}