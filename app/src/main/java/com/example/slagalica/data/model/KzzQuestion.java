package com.example.slagalica.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Pitanje za igru "Ko zna zna" — dokument u Firestore kolekciji "koZnaZna".
 */
public class KzzQuestion {

    private String text;
    private List<String> answers;
    private int correctIndex;

    public KzzQuestion() {
        // obavezan prazan konstruktor za Firestore
        answers = new ArrayList<>();
    }

    public KzzQuestion(String text, List<String> answers, int correctIndex) {
        this.text = text;
        this.answers = answers;
        this.correctIndex = correctIndex;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<String> getAnswers() { return answers; }
    public void setAnswers(List<String> answers) { this.answers = answers; }

    public int getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(int correctIndex) { this.correctIndex = correctIndex; }
}
