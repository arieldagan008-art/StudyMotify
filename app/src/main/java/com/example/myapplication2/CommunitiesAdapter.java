package com.example.myapplication2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CommunitiesAdapter extends RecyclerView.Adapter<CommunitiesAdapter.CommunityVH> {

    public interface OnCommunityClickListener {
        void onCommunityClicked(Community community);
    }

    private static final String[] CATEGORY_ICONS = {
            "🧬", "📜", "⚗️", "🧠", "➕", "🔭", "💻", "📚", "🌍", "🎓"
    };

    private final List<Community>           communities;
    private final OnCommunityClickListener  listener;

    public CommunitiesAdapter(List<Community> communities, OnCommunityClickListener listener) {
        this.communities = communities;
        this.listener    = listener;
    }

    @NonNull
    @Override
    public CommunityVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_community_card, parent, false);
        return new CommunityVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CommunityVH holder, int position) {
        Community c = communities.get(position);
        holder.tvName.setText(c.getName());
        holder.tvCategory.setText(c.getCategory().isEmpty() ? "General" : c.getCategory());
        // Pick an emoji icon based on the community name hash
        int iconIdx = Math.abs(c.getName().hashCode()) % CATEGORY_ICONS.length;
        holder.tvIcon.setText(CATEGORY_ICONS[iconIdx]);
        holder.itemView.setOnClickListener(v -> listener.onCommunityClicked(c));
    }

    @Override
    public int getItemCount() { return communities.size(); }

    static class CommunityVH extends RecyclerView.ViewHolder {
        TextView tvName, tvCategory, tvIcon;

        CommunityVH(@NonNull View itemView) {
            super(itemView);
            tvName     = itemView.findViewById(R.id.tv_community_name);
            tvCategory = itemView.findViewById(R.id.tv_community_category);
            tvIcon     = itemView.findViewById(R.id.tv_community_icon);
        }
    }
}
