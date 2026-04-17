package com.example.myapplication2;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SharedLinksAdapter extends RecyclerView.Adapter<SharedLinksAdapter.LinkViewHolder> {

    private final Context        context;
    private final List<SharedLink> links;

    public SharedLinksAdapter(Context context, List<SharedLink> links) {
        this.context = context;
        this.links   = links;
    }

    @NonNull
    @Override
    public LinkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_shared_link, parent, false);
        return new LinkViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LinkViewHolder holder, int position) {
        SharedLink link = links.get(position);

        holder.tvName.setText(link.getResourceName());
        holder.tvUrl.setText(link.getLinkUrl());
        holder.tvAuthor.setText("Shared by " + link.getAuthorEmail());
        holder.tvTime.setText(formatTimeAgo(link.timestamp));

        // Entire card opens the URL in the system browser
        holder.itemView.setOnClickListener(v -> openUrl(link.getLinkUrl()));
    }

    private void openUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(context, "Invalid link.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Ensure the URL has a scheme so the browser can handle it
        String normalised = url.trim();
        if (!normalised.startsWith("http://") && !normalised.startsWith("https://")) {
            normalised = "https://" + normalised;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalised));
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Could not open link.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Returns a human-friendly "X ago" string or falls back to a date. */
    private String formatTimeAgo(long timestamp) {
        if (timestamp <= 0) return "";
        long diffMs = System.currentTimeMillis() - timestamp;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs);
        long hours   = TimeUnit.MILLISECONDS.toHours(diffMs);
        long days    = TimeUnit.MILLISECONDS.toDays(diffMs);
        if (minutes < 1)  return "Just now";
        if (minutes < 60) return minutes + "m ago";
        if (hours   < 24) return hours   + "h ago";
        if (days    < 7)  return days    + "d ago";
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    @Override
    public int getItemCount() { return links.size(); }

    static class LinkViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvUrl, tvAuthor, tvTime;

        LinkViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName   = itemView.findViewById(R.id.tv_link_name);
            tvUrl    = itemView.findViewById(R.id.tv_link_url);
            tvAuthor = itemView.findViewById(R.id.tv_link_author);
            tvTime   = itemView.findViewById(R.id.tv_link_time);
        }
    }
}
