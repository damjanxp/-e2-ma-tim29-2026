package com.example.slagalica.ui.ranking;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.util.AvatarProvider;
import com.example.slagalica.util.Constants;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter za listu igrača na rang listi. Prva tri mesta dobijaju posebne
 * vizuelne efekte (zlatna/srebrna/bronzana pozadina, medalja umesto broja,
 * kruna za prvo mesto).
 */
public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.RankViewHolder> {

    private static final String[] MEDALS = {"🥇", "🥈", "🥉"};

    private final List<User> users = new ArrayList<>();
    private String type = Constants.LEADERBOARD_TYPE_WEEKLY;

    public void setItems(@NonNull List<User> items, @NonNull String type) {
        this.type = type;
        users.clear();
        users.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RankViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ranking, parent, false);
        return new RankViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RankViewHolder holder, int position) {
        User user = users.get(position);
        int rank = position + 1;
        int stars = Constants.LEADERBOARD_TYPE_MONTHLY.equals(type)
                ? user.getMonthlyStars() : user.getWeeklyStars();

        holder.ivAvatar.setImageResource(AvatarProvider.getDrawableForStored(user.getAvatarUrl()));
        holder.tvUsername.setText(user.getUsername());
        holder.tvStars.setText(holder.itemView.getContext()
                .getString(R.string.ranking_item_stars, stars));

        if (rank <= 3) {
            holder.tvRankNumber.setVisibility(View.GONE);
            holder.tvMedal.setVisibility(View.VISIBLE);
            holder.tvMedal.setText(MEDALS[rank - 1]);
            holder.card.setCardElevation(dp(holder, rank == 1 ? 8f : 5f));
            holder.rowContent.setBackgroundResource(backgroundFor(rank));
            holder.tvCrown.setVisibility(rank == 1 ? View.VISIBLE : View.GONE);
        } else {
            holder.tvRankNumber.setVisibility(View.VISIBLE);
            holder.tvRankNumber.setText(String.valueOf(rank));
            holder.tvMedal.setVisibility(View.GONE);
            holder.rowContent.setBackgroundResource(0);
            holder.card.setCardElevation(dp(holder, 1f));
            holder.tvCrown.setVisibility(View.GONE);
        }
    }

    private int backgroundFor(int rank) {
        switch (rank) {
            case 1: return R.drawable.bg_rank_gold;
            case 2: return R.drawable.bg_rank_silver;
            default: return R.drawable.bg_rank_bronze;
        }
    }

    private float dp(RankViewHolder holder, float value) {
        return value * holder.itemView.getContext().getResources().getDisplayMetrics().density;
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class RankViewHolder extends RecyclerView.ViewHolder {

        final MaterialCardView card;
        final LinearLayout rowContent;
        final TextView tvRankNumber;
        final TextView tvMedal;
        final ShapeableImageView ivAvatar;
        final TextView tvCrown;
        final TextView tvUsername;
        final TextView tvStars;

        RankViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardRank);
            rowContent = itemView.findViewById(R.id.rowRankContent);
            tvRankNumber = itemView.findViewById(R.id.tvRankNumber);
            tvMedal = itemView.findViewById(R.id.tvRankMedal);
            ivAvatar = itemView.findViewById(R.id.ivRankAvatar);
            tvCrown = itemView.findViewById(R.id.tvRankCrown);
            tvUsername = itemView.findViewById(R.id.tvRankUsername);
            tvStars = itemView.findViewById(R.id.tvRankStars);
        }
    }
}
