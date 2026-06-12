package com.example.slagalica.data.model;

/**
 * Odgovor jednog igrača na jedno pitanje u igri "Ko zna zna".
 * Čuva se u Realtime Database pod {@code matches/{matchId}/kzz/answers/{q}/{uid}}.
 * {@code answerIndex == -1} znači da igrač nije odgovorio (isteklo vreme).
 */
public class KzzAnswer {

    private int answerIndex;
    private long elapsedMs;
    private boolean correct;

    public KzzAnswer() {
        // obavezan prazan konstruktor za Realtime Database
    }

    public KzzAnswer(int answerIndex, long elapsedMs, boolean correct) {
        this.answerIndex = answerIndex;
        this.elapsedMs = elapsedMs;
        this.correct = correct;
    }

    public int getAnswerIndex() { return answerIndex; }
    public void setAnswerIndex(int answerIndex) { this.answerIndex = answerIndex; }

    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
}
