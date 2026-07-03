package com.example.slagalica.data.model;

/**
 * Slagalica za igru "Asocijacije".
 *
 * 4 kolone (A–D), svaka sa 4 naputka i jednim rešenjem kolone.
 * Rešenja kolona zajedno asociraju na finalno rešenje.
 *
 * <p>Sadržaj se učitava iz Firestore kolekcije {@code asocijacije} preko
 * {@link com.example.slagalica.data.repository.GameContentRepository#loadAsocijacijePuzzles};
 * oba igrača dobijaju isti objekat za istu rundu (upisan u meč kroz
 * {@code MatchRepository#writeAsocijacijePuzzles}).</p>
 */
public class AsocijacijePuzzle {

    private final String[][] clues;       // [col 0-3][row 0-3]
    private final String[] colSolutions;  // [col 0-3]
    private final String finalSolution;

    public AsocijacijePuzzle(String[][] clues, String[] colSolutions, String finalSolution) {
        this.clues = clues;
        this.colSolutions = colSolutions;
        this.finalSolution = finalSolution;
    }

    public String getClue(int col, int row) {
        return clues[col][row];
    }

    public String getColSolution(int col) {
        return colSolutions[col];
    }

    public String getFinalSolution() {
        return finalSolution;
    }
}
