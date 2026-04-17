package com.example.myapplication2;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageVH> {

    private final List<Message> messages;
    private final String        currentUid;

    public MessagesAdapter(List<Message> messages, String currentUid) {
        this.messages   = messages;
        this.currentUid = currentUid != null ? currentUid : "";
    }

    @NonNull
    @Override
    public MessageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageVH holder, int position) {
        Message msg  = messages.get(position);
        boolean mine = currentUid.equals(msg.getSenderId());

        holder.tvText.setText(msg.getText());
        holder.tvTime.setText(formatTime(msg.timestamp));

        if (mine) {
            // Right-align, blue bubble, hide sender name, white text
            holder.bubble.setBackground(
                    ContextCompat.getDrawable(holder.itemView.getContext(),
                            R.drawable.bg_bubble_sent));
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) holder.bubble.getLayoutParams();
            lp.gravity = Gravity.END;
            holder.bubble.setLayoutParams(lp);
            holder.tvSender.setVisibility(View.GONE);
            holder.tvText.setTextColor(0xFFFFFFFF);
            holder.tvTime.setTextColor(0xCCFFFFFF);
        } else {
            // Left-align, grey bubble, show sender name, dark text
            holder.bubble.setBackground(
                    ContextCompat.getDrawable(holder.itemView.getContext(),
                            R.drawable.bg_bubble_received));
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) holder.bubble.getLayoutParams();
            lp.gravity = Gravity.START;
            holder.bubble.setLayoutParams(lp);
            holder.tvSender.setVisibility(View.VISIBLE);
            holder.tvSender.setText(msg.getSenderName());
            holder.tvText.setTextColor(0xFF1A1A1A);
            holder.tvTime.setTextColor(0xFF888888);
        }
    }

    private String formatTime(long ts) {
        if (ts <= 0) return "";
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class MessageVH extends RecyclerView.ViewHolder {
        LinearLayout bubble;
        TextView     tvSender, tvText, tvTime;

        MessageVH(@NonNull View itemView) {
            super(itemView);
            bubble   = itemView.findViewById(R.id.bubble);
            tvSender = itemView.findViewById(R.id.tv_sender_name);
            tvText   = itemView.findViewById(R.id.tv_message_text);
            tvTime   = itemView.findViewById(R.id.tv_message_time);
        }
    }
}
