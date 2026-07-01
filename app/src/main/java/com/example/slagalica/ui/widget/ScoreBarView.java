package com.example.slagalica.ui.widget;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.example.slagalica.R;
import com.google.android.material.imageview.ShapeableImageView;

/**
 * Traka sa rezultatom meča — prikazuje avatar, nadimak i ukupan skor
 * osvojen tokom meča za oba igrača, sa odbrojavanjem za tekuću rundu
 * na sredini. Skor koji se prikazuje je zbir prethodno završenih igara
 * u meču, ne trenutni rezultat igre koja je u toku.
 */
public class ScoreBarView extends FrameLayout {

    private final ShapeableImageView myAvatar;
    private final TextView myScore;
    private final TextView myName;
    private final TextView timer;
    private final ShapeableImageView opponentAvatar;
    private final TextView opponentScore;
    private final TextView opponentName;

    public ScoreBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.view_score_bar, this, true);

        myAvatar       = findViewById(R.id.barMyAvatar);
        myScore        = findViewById(R.id.barMyScore);
        myName         = findViewById(R.id.barMyName);
        timer          = findViewById(R.id.barTimer);
        opponentAvatar = findViewById(R.id.barOpponentAvatar);
        opponentScore  = findViewById(R.id.barOpponentScore);
        opponentName   = findViewById(R.id.barOpponentName);

        myName.setPaintFlags(myName.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        opponentName.setPaintFlags(opponentName.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }

    public void setMyPlayer(@DrawableRes int avatarRes, String name) {
        myAvatar.setImageResource(avatarRes);
        myName.setText(name);
    }

    public void setOpponentPlayer(@DrawableRes int avatarRes, String name) {
        opponentAvatar.setImageResource(avatarRes);
        opponentName.setText(name);
    }

    public void setScores(int myTotal, int opponentTotal) {
        myScore.setText(String.valueOf(myTotal));
        opponentScore.setText(String.valueOf(opponentTotal));
    }

    public void setTimeLeft(int secondsLeft) {
        timer.setText(String.valueOf(Math.max(secondsLeft, 0)));
    }

    /** Postavlja proizvoljan tekst u tajmer (npr. "–" dok se čeka početak runde). */
    public void setTimeText(String text) {
        timer.setText(text);
    }
}
