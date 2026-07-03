package com.example.slagalica.ui.dailychallenge;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.DailyChallengeState;
import com.example.slagalica.util.Constants;
import com.google.android.material.card.MaterialCardView;

/**
 * Prikazuje listu dnevnih izazova iz {@link Constants#DAILY_CHALLENGE_IDS} —
 * lista automatski raste kada se registar proširi novim izazovima, bez
 * ikakvih izmena u ovoj klasi (vidi {@link DailyChallengesActivity}).
 */
public class DailyChallengeAdapter extends RecyclerView.Adapter<DailyChallengeAdapter.ViewHolder> {

    @Nullable private DailyChallengeState state;

    public void setState(@NonNull DailyChallengeState newState) {
        this.state = newState;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_challenge, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String id = Constants.DAILY_CHALLENGE_IDS[position];
        DailyChallengeCatalog.Entry entry = DailyChallengeCatalog.entryFor(id);
        boolean completed = state != null && state.isCompleted(id);
        Context ctx = holder.itemView.getContext();

        holder.tvIcon.setText(entry != null ? entry.icon : "🎯");
        holder.tvTitle.setText(entry != null ? ctx.getString(entry.titleRes) : id);
        holder.tvDesc.setText(entry != null ? ctx.getString(entry.descRes) : "");
        holder.tvStatus.setText(completed
                ? R.string.daily_challenge_status_complete
                : R.string.daily_challenge_status_incomplete);

        int bgColor = ContextCompat.getColor(ctx, completed
                ? R.color.daily_challenge_complete_bg : R.color.daily_challenge_incomplete_bg);
        int borderColor = ContextCompat.getColor(ctx, completed
                ? R.color.daily_challenge_complete_border : R.color.daily_challenge_incomplete_border);
        int textColor = ContextCompat.getColor(ctx, completed
                ? R.color.daily_challenge_complete_text : R.color.daily_challenge_incomplete_text);

        holder.card.setCardBackgroundColor(bgColor);
        holder.card.setStrokeColor(borderColor);
        holder.tvStatus.setTextColor(textColor);
    }

    @Override
    public int getItemCount() {
        return Constants.DAILY_CHALLENGE_IDS.length;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView tvIcon;
        final TextView tvTitle;
        final TextView tvDesc;
        final TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            tvIcon = itemView.findViewById(R.id.tvDailyIcon);
            tvTitle = itemView.findViewById(R.id.tvDailyTitle);
            tvDesc = itemView.findViewById(R.id.tvDailyDesc);
            tvStatus = itemView.findViewById(R.id.tvDailyStatus);
        }
    }
}
