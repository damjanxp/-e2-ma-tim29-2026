package com.example.slagalica.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Jedna runda igre "Spojnice" — dokument u Firestore kolekciji "spojnice".
 * {@code correctMap.get(i)} je indeks stavke desne kolone koja je tačan par
 * za i-tu stavku leve kolone.
 */
public class SpojnicePuzzle {

    private String criterion;
    private List<String> leftItems;
    private List<String> rightItems;
    private List<Long> correctMap;

    public SpojnicePuzzle() {
        // obavezan prazan konstruktor za Firestore
        leftItems = new ArrayList<>();
        rightItems = new ArrayList<>();
        correctMap = new ArrayList<>();
    }

    public SpojnicePuzzle(String criterion, List<String> leftItems,
                          List<String> rightItems, List<Long> correctMap) {
        this.criterion = criterion;
        this.leftItems = leftItems;
        this.rightItems = rightItems;
        this.correctMap = correctMap;
    }

    public String getCriterion() { return criterion; }
    public void setCriterion(String criterion) { this.criterion = criterion; }

    public List<String> getLeftItems() { return leftItems; }
    public void setLeftItems(List<String> leftItems) { this.leftItems = leftItems; }

    public List<String> getRightItems() { return rightItems; }
    public void setRightItems(List<String> rightItems) { this.rightItems = rightItems; }

    public List<Long> getCorrectMap() { return correctMap; }
    public void setCorrectMap(List<Long> correctMap) { this.correctMap = correctMap; }

    /** Vraća indeks tačnog para u desnoj koloni za zadati levi indeks. */
    public int correctRightFor(int leftIdx) {
        return correctMap.get(leftIdx).intValue();
    }
}
