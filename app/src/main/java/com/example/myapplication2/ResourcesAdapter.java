package com.example.myapplication2;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.util.List;

public class ResourcesAdapter extends RecyclerView.Adapter<ResourcesAdapter.ResourceVH> {

    private final Context        context;
    private final List<Resource> resources;
    private final String         communityId;
    private final String         currentUid;

    public ResourcesAdapter(Context context, List<Resource> resources,
                            String communityId, String currentUid) {
        this.context     = context;
        this.resources   = resources;
        this.communityId = communityId;
        this.currentUid  = currentUid;
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
        holder.tvType.getBackground().setTint(isExam ? 0xFFE65100 : 0xFF00796B);

        // Like button state
        boolean liked = res.isLikedBy(currentUid);
        updateLikeButton(holder, res.getLikesCount(), liked);

        holder.btnLike.setOnClickListener(v -> toggleLike(res, holder));

        holder.itemView.setOnClickListener(v -> openUrl(res.getUrl()));
    }

    // ─── Like toggle ──────────────────────────────────────────────────────────

    private void toggleLike(@NonNull Resource res, @NonNull ResourceVH holder) {
        if (currentUid == null || currentUid.isEmpty()) {
            Toast.makeText(context, "Sign in to like resources.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (res.getId().isEmpty()) return;

        DatabaseReference resourceRef = FirebaseHelper.getInstance()
                .getDatabase()
                .getReference("communities")
                .child(communityId)
                .child("resources")
                .child(res.getId());

        boolean alreadyLiked = res.isLikedBy(currentUid);

        // Toggle likes/{uid} first
        DatabaseReference likeRef = resourceRef.child("likes").child(currentUid);
        if (alreadyLiked) {
            likeRef.removeValue();
        } else {
            likeRef.setValue(true);
        }

        // Atomically increment / decrement likesCount
        resourceRef.child("likesCount").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                long count = 0;
                if (currentData.getValue(Long.class) != null) {
                    count = currentData.getValue(Long.class);
                }
                if (alreadyLiked) {
                    count = Math.max(0, count - 1);
                } else {
                    count = count + 1;
                }
                currentData.setValue(count);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error,
                                   boolean committed,
                                   @Nullable DataSnapshot currentData) {
                // The ValueEventListener in ChatActivity will refresh the list
            }
        });

        // Optimistic local update so the UI responds immediately
        if (alreadyLiked) {
            if (res.likes != null) res.likes.remove(currentUid);
            res.likesCount = Math.max(0, res.likesCount - 1);
        } else {
            if (res.likes == null) res.likes = new java.util.HashMap<>();
            res.likes.put(currentUid, true);
            res.likesCount = res.likesCount + 1;
        }
        updateLikeButton(holder, res.getLikesCount(), !alreadyLiked);
    }

    private void updateLikeButton(@NonNull ResourceVH holder, long count, boolean liked) {
        if (liked) {
            holder.btnLike.setText("❤  Liked");
            holder.btnLike.setTextColor(0xFFE53935);
            holder.btnLike.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFFEBEE));
        } else {
            holder.btnLike.setText("♡  Like");
            holder.btnLike.setTextColor(0xFF666666);
            holder.btnLike.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFF5F5F5));
        }
        holder.tvLikesCount.setText(count == 1 ? "1 like" : count + " likes");
    }

    // ─── Open URL ─────────────────────────────────────────────────────────────

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

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    static class ResourceVH extends RecyclerView.ViewHolder {
        TextView tvIcon, tvTitle, tvUrl, tvType, tvUploader, tvLikesCount;
        Button   btnLike;

        ResourceVH(@NonNull View itemView) {
            super(itemView);
            tvIcon       = itemView.findViewById(R.id.tv_resource_icon);
            tvTitle      = itemView.findViewById(R.id.tv_resource_title);
            tvUrl        = itemView.findViewById(R.id.tv_resource_url);
            tvType       = itemView.findViewById(R.id.tv_resource_type);
            tvUploader   = itemView.findViewById(R.id.tv_resource_uploader);
            tvLikesCount = itemView.findViewById(R.id.tv_likes_count);
            btnLike      = itemView.findViewById(R.id.btn_like);
        }
    }
}
