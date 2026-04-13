package com.eduassist.eduassist.dto;

import java.util.List;

public class UpdateQuestionRequest {

    private String prompt;
    private String explanation;
    private Integer points;
    private List<UpdateOptionRequest> options;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public List<UpdateOptionRequest> getOptions() {
        return options;
    }

    public void setOptions(List<UpdateOptionRequest> options) {
        this.options = options;
    }

    public static class UpdateOptionRequest {
        private String optionText;
        private Boolean correct;

        public String getOptionText() {
            return optionText;
        }

        public void setOptionText(String optionText) {
            this.optionText = optionText;
        }

        public Boolean getCorrect() {
            return correct;
        }

        public void setCorrect(Boolean correct) {
            this.correct = correct;
        }
    }
}