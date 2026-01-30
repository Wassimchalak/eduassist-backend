package com.eduassist.eduassist.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "option_item")
public class OptionItem {

    @Id
    @GeneratedValue
    @Column(name = "option_id")
    private UUID optionId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "option_text", nullable = false)
    private String optionText;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @PrePersist
    protected void onCreate() {
        if (this.isCorrect == null) {
            this.isCorrect = false;
        }
    }

    public UUID getOptionId() {
        return optionId;
    }

    public void setOptionId(UUID optionId) {
        this.optionId = optionId;
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }

    public Boolean getCorrect() {
        return isCorrect;
    }

    public void setCorrect(Boolean correct) {
        isCorrect = correct;
    }
}

