package com.example.slagalica.ui.challenge;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter za listu otvorenih izazova u regionu.
 */
public class ChallengeAdapter extends RecyclerView.Adapter<ChallengeAdapter.ChallengeViewHolder> {

    /** Okida se na klik na jedan izazov u listi. */
    public interface OnChallengeClickListener {
        void onChallengeClicked(@NonNull Challenge challenge);
    }

    private final List<Challenge> challenges = new ArrayList<>();
    @Nullable private final OnChallengeClickListener listener;

    public ChallengeAdapter(@Nullable OnChallengeClickListener listener) {
        this.listener = listener;
    }

    public void setItems(@NonNull List<Challenge> items) {
        challenges.clear();
        challenges.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChallengeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_challenge, parent, false);
        return new ChallengeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChallengeViewHolder holder, int position) {
        Challenge challenge = challenges.get(position);
        Context context = holder.itemView.getContext();

        holder.tvHostName.setText(challenge.getHostName());
        holder.tvStake.setText(context.getString(R.string.challenge_stake_format,
                challenge.getStakeStars(), challenge.getStakeTokens()));

        Map<String, ?> participants = challenge.getParticipants();
        int count = participants != null ? participants.size() : 0;
        holder.tvParticipants.setText(context.getString(R.string.challenge_participants_format, count, 4));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChallengeClicked(challenge);
            }
        });
    }

    @Override
    public int getItemCount() {
        return challenges.size();
    }

    static class ChallengeViewHolder extends RecyclerView.ViewHolder {

        final TextView tvHostName;
        final TextView tvStake;
        final TextView tvParticipants;

        ChallengeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHostName = itemView.findViewById(R.id.tvHostName);
            tvStake = itemView.findViewById(R.id.tvStake);
            tvParticipants = itemView.findViewById(R.id.tvParticipants);
        }
    }
}
