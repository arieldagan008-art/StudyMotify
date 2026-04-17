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

import java.util.List;

public class ResourcesAdapter extends RecyclerView.Adapter<ResourcesAdapter.ResourceVH> {

    private final Context        context;
    private final List<Resource> resources;

    public ResourcesAdapter(Context context, List<Resource> resources) {
        this.context   = context;
        this.resources = resources;
    }

    @NonNull
    @Override
    public ResourceVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_resource, parent, false);
        return new ResourceVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ResourceVH holder, int position) {
        Resource res = resources.get(position);

        holder.tvTitle.setText(res.getTitle());
        holder.tvUrl.setText(res.getUrl());
        holder.tvUploader.setText("by " + res.getUploaderName());

        boolean isExam = "Exam".equalsIgnoreCase(res.getType());
        holder.tvIcon.setText(isExam ? "📝" : "📄");
        holder.tvType.setText(res.getType());
        // Badge colour: orange for Exam, teal for Summary
        holder.tvType.getBackground().setTint(isExam ? 0xFFE65100 : 0xFF00796B);

        holder.itemView.setOnClickListener(v -> openUrl(res.getUrl()));
    }

    private void openUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(context, "Invalid link.", Toast.LENGTH_SHORT).show();
            return;
        }
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        try {
            context.startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(normalized)));
        } catch (Exception e) {
            Toast.makeText(context, "Could not open link.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() { return resources.size(); }

    static class ResourceVH extends RecyclerView.ViewHolder {
        TextView tvIcon, tvTitle, tvUrl, tvType, tvUploader;

        ResourceVH(@NonNull View itemView) {
            super(itemView);
            tvIcon     = itemView.findViewById(R.id.tv_resource_icon);
            tvTitle    = itemView.findViewById(R.id.tv_resource_title);
            tvUrl      = itemView.findViewById(R.id.tv_resource_url);
            tvType     = itemView.findViewById(R.id.tv_resource_type);
            tvUploader = itemView.findViewById(R.id.tv_resource_uploader);
        }
    }
}
