package com.example.slagalica.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Message;
import com.example.slagalica.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter za listu poruka u regionalnom četu.
 *
 * <p>Poruke poslate od strane trenutnog korisnika se prikazuju DESNO,
 * a poruke ostalih korisnika LEVO.</p>
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private static final int VIEW_TYPE_LEFT = 0;
    private static final int VIEW_TYPE_RIGHT = 1;

    private final List<Message> messages = new ArrayList<>();
    private final String myUid;

    public ChatAdapter(@NonNull String myUid) {
        this.myUid = myUid;
    }

    public void setItems(@NonNull List<Message> items) {
        messages.clear();
        messages.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        return myUid.equals(message.getSenderUid()) ? VIEW_TYPE_RIGHT : VIEW_TYPE_LEFT;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = viewType == VIEW_TYPE_RIGHT
                ? R.layout.item_chat_message_right
                : R.layout.item_chat_message_left;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.tvSenderName.setText(message.getSenderName());
        holder.tvMessageText.setText(message.getText());
        holder.tvMessageTime.setText(DateUtils.formatTimestamp(message.getTimestamp()));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        final TextView tvSenderName;
        final TextView tvMessageText;
        final TextView tvMessageTime;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            tvMessageTime = itemView.findViewById(R.id.tvMessageTime);
        }
    }
}
