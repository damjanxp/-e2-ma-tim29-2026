package com.example.slagalica.data.model;

/**
 * Slagalica za igru "Asocijacije".
 *
 * 4 kolone (A–D), svaka sa 4 naputka i jednim rešenjem kolone.
 * Rešenja kolona zajedno asociraju na finalno rešenje.
 *
 * Za prelaz na 1v1: učitaj iz Firestore-a umesto hardkodiranih primera;
 * oba igrača dobijaju isti objekat za istu rundu.
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

    public static AsocijacijePuzzle[] samplePuzzles() {
        return new AsocijacijePuzzle[]{

            new AsocijacijePuzzle(
                new String[][]{
                    {"Tropik", "Kokos", "Surfer", "Plaža"},      // A → MORE
                    {"Skija", "Sankanje", "Sneg", "Planina"},     // B → ZIMA
                    {"Šatori", "Reka", "Priroda", "Šuma"},        // C → KAMPOVANJE
                    {"Bazen", "Sunce", "Odmor", "Letovanje"}      // D → LETO
                },
                new String[]{"MORE", "ZIMA", "KAMPOVANJE", "LETO"},
                "GODIŠNJI ODMOR"
            ),

            new AsocijacijePuzzle(
                new String[][]{
                    {"Pasta", "Pica", "Rim", "Venecija"},         // A → ITALIJA
                    {"Baguette", "Pariz", "Ajfelov", "Vino"},     // B → FRANCUSKA
                    {"Flamenco", "Toreador", "Barselona", "Šerija"}, // C → ŠPANIJA
                    {"Big Ben", "London", "Čaj", "Kruna"}         // D → ENGLESKA
                },
                new String[]{"ITALIJA", "FRANCUSKA", "ŠPANIJA", "ENGLESKA"},
                "ZAPADNA EVROPA"
            )
        };
    }
}
