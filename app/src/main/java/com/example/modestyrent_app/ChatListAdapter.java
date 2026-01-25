package com.example.modestyrent_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatRoomViewHolder> {

    private List<ChatRoom> chatRooms;
    private OnChatRoomClickListener listener;

    public interface OnChatRoomClickListener {
        void onChatRoomClick(ChatRoom chatRoom);
    }

    public ChatListAdapter(List<ChatRoom> chatRooms, OnChatRoomClickListener listener) {
        this.chatRooms = chatRooms;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatRoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_list, parent, false);
        return new ChatRoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatRoomViewHolder holder, int position) {
        ChatRoom chatRoom = chatRooms.get(position);
        holder.bind(chatRoom);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChatRoomClick(chatRoom);
            }
        });
    }

    @Override
    public int getItemCount() {
        return chatRooms.size();
    }

    static class ChatRoomViewHolder extends RecyclerView.ViewHolder {
        private TextView tvUserName, tvLastMessage, tvTime, tvAvatar, tvUnreadCount;
        private MaterialCardView badgeUnread;

        public ChatRoomViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
            badgeUnread = itemView.findViewById(R.id.badgeUnread);
        }

        void bind(ChatRoom chatRoom) {
            // Set user name
            String userName = chatRoom.getOtherUserName();
            if (userName != null && !userName.isEmpty()) {
                tvUserName.setText(userName);
            } else {
                tvUserName.setText("User");
            }

            // Set last message
            String lastMessage = chatRoom.getLastMessage();
            if (lastMessage != null && !lastMessage.isEmpty()) {
                tvLastMessage.setText(lastMessage);
            } else {
                tvLastMessage.setText("Start a conversation");
            }

            // Set time
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvTime.setText(sdf.format(chatRoom.getLastMessageTime()));

            // Set avatar initials
            String initials = getInitials(chatRoom.getOtherUserName());
            tvAvatar.setText(initials);

            // Show/hide unread badge
            int unreadCount = chatRoom.getUnreadCount();
            if (unreadCount > 0) {
                badgeUnread.setVisibility(View.VISIBLE);
                if (unreadCount > 99) {
                    tvUnreadCount.setText("99+");
                } else {
                    tvUnreadCount.setText(String.valueOf(unreadCount));
                }
            } else {
                badgeUnread.setVisibility(View.GONE);
            }
        }

        private String getInitials(String name) {
            if (name == null || name.trim().isEmpty()) {
                return "U";
            }

            String[] words = name.trim().split("\\s+");
            StringBuilder initials = new StringBuilder();

            if (words.length >= 1 && words[0].length() > 0) {
                initials.append(words[0].substring(0, 1).toUpperCase());
            }
            if (words.length >= 2 && words[1].length() > 0) {
                initials.append(words[1].substring(0, 1).toUpperCase());
            }

            if (initials.length() == 0 && name.length() > 0) {
                initials.append(name.substring(0, 1).toUpperCase());
            }

            return initials.toString();
        }
    }
}