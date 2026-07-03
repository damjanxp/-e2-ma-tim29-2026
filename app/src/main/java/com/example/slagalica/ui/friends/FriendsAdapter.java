package com.example.slagalica.ui.friends;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.util.AvatarProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter za listu prijatelja ({@code activity_friends.xml}) — prikazuje
 * avatar, korisničko ime, ligu i mesečne zvezde svakog prijatelja, sa
 * dugmetom za poziv na partiju.
 */
public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {

    /** Poziva se klikom na dugme "Igraj" za konkretnog prijatelja. */
    public interface OnPlayClickListener {
        void onPlayClicked(@NonNull User friend);
    }

    private final List<User> items = new ArrayList<>();
    private final OnPlayClickListener listener;
    private final String[] leagueNames;

    public FriendsAdapter(@NonNull OnPlayClickListener listener, @NonNull String[] leagueNames) {
        this.listener = listener;
        this.leagueNames = leagueNames;
    }

    public void setItems(@NonNull List<User> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User friend = items.get(position);
        holder.ivAvatar.setImageResource(AvatarProvider.getDrawableForStored(friend.getAvatarUrl()));
        holder.tvUsername.setText(friend.getUsername());

        String league = leagueName(friend.getCurrentLeague());
        holder.tvDetails.setText(holder.itemView.getContext().getString(
                R.string.friends_item_details, league, friend.getMonthlyStars()));

        holder.btnPlay.setOnClickListener(v -> listener.onPlayClicked(friend));
    }

    private String leagueName(int league) {
        if (league < 0 || league >= leagueNames.length) {
            return leagueNames.length > 0 ? leagueNames[0] : "";
        }
        return leagueNames[league];
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView ivAvatar;
        final TextView tvUsername;
        final TextView tvDetails;
        final MaterialButton btnPlay;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivFriendAvatar);
            tvUsername = itemView.findViewById(R.id.tvFriendUsername);
            tvDetails = itemView.findViewById(R.id.tvFriendDetails);
            btnPlay = itemView.findViewById(R.id.btnFriendPlay);
        }
    }
}
