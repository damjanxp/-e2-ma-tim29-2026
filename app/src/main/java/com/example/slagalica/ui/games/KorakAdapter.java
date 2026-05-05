package com.example.slagalica.ui.games;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Korak;
import com.example.slagalica.data.model.KorakState;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * Adapter za prikaz 7 koraka (hint-ova) u igri Korak po korak.
 * Boja pozadine kartice odražava trenutni {@link KorakState} svakog koraka.
 */
public class KorakAdapter extends RecyclerView.Adapter<KorakAdapter.KorakViewHolder> {

    private static final int COLOR_ZAKLJUCAN = Color.parseColor("#FFE0E0E0");
    private static final int COLOR_OTVOREN   = Color.parseColor("#FFFFFFFF");
    private static final int COLOR_POGODJEN  = Color.parseColor("#FFC8E6C9");
    private static final int COLOR_PROMASEN  = Color.parseColor("#FFFFCDD2");

    private final List<Korak> koraci;

    public KorakAdapter(@NonNull List<Korak> koraci) {
        this.koraci = koraci;
    }

    @NonNull
    @Override
    public KorakViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_korak, parent, false);
        return new KorakViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull KorakViewHolder holder, int position) {
        Korak korak = koraci.get(position);

        holder.tvNumber.setText(String.valueOf(korak.getRedniBroj()));

        switch (korak.getState()) {
            case ZAKLJUCAN:
                holder.tvHint.setText("🔒");
                holder.cvStep.setCardBackgroundColor(COLOR_ZAKLJUCAN);
                break;
            case OTVOREN:
                holder.tvHint.setText(korak.getHint());
                holder.cvStep.setCardBackgroundColor(COLOR_OTVOREN);
                break;
            case POGODJEN:
                holder.tvHint.setText(korak.getHint());
                holder.cvStep.setCardBackgroundColor(COLOR_POGODJEN);
                break;
            case PROMASEN:
                holder.tvHint.setText(korak.getHint());
                holder.cvStep.setCardBackgroundColor(COLOR_PROMASEN);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return koraci.size();
    }

    /**
     * Pronalazi prvi korak sa stanjem {@link KorakState#ZAKLJUCAN},
     * postavlja ga na {@link KorakState#OTVOREN} i osvežava tu stavku u listi.
     *
     * @return indeks otvorenog koraka, ili -1 ako svi koraci već nisu zaključani
     */
    public int otvoriSledeci() {
        for (int i = 0; i < koraci.size(); i++) {
            if (koraci.get(i).getState() == KorakState.ZAKLJUCAN) {
                koraci.get(i).setState(KorakState.OTVOREN);
                notifyItemChanged(i);
                return i;
            }
        }
        return -1;
    }

    // -------------------------------------------------------------------------

    static class KorakViewHolder extends RecyclerView.ViewHolder {

        final MaterialCardView cvStep;
        final TextView tvNumber;
        final TextView tvHint;

        KorakViewHolder(@NonNull View itemView) {
            super(itemView);
            cvStep   = itemView.findViewById(R.id.cvStep);
            tvNumber = itemView.findViewById(R.id.tvNumber);
            tvHint   = itemView.findViewById(R.id.tvHint);
        }
    }
}

