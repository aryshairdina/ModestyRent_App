package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class activity_chat_list extends AppCompatActivity {

    private RecyclerView rvChatList;
    private LinearLayout emptyState;
    private ImageView backButton;

    private ChatListAdapter chatListAdapter;
    private final List<ChatRoom> chatRooms = new ArrayList<>();

    private DatabaseReference chatsRef, usersRef;
    private String currentUserId;

    private ValueEventListener chatListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        initializeViews();
        initializeFirebase();
        setupRecyclerView();
        loadChatRooms();
        setupClickListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatsRef != null && chatListener != null) {
            chatsRef.removeEventListener(chatListener);
        }
    }

    private void initializeViews() {
        rvChatList = findViewById(R.id.rvChatList);
        emptyState = findViewById(R.id.emptyState);
        backButton = findViewById(R.id.backButton);
    }

    private void initializeFirebase() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        chatsRef = database.getReference("chats");
        usersRef = database.getReference("users");
    }

    private void setupRecyclerView() {
        chatListAdapter = new ChatListAdapter(chatRooms, this::onChatRoomClick);
        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.setAdapter(chatListAdapter);
    }

    /**
     * Listen to /chats in real-time and rebuild list whenever lastMessage / lastMessageTime changes.
     */
    private void loadChatRooms() {
        if (chatListener != null) {
            chatsRef.removeEventListener(chatListener);
        }

        chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chatRooms.clear();

                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId == null) continue;

                    // ----- Check if current user is participant -----
                    boolean isParticipant = false;

                    // Option 1: participants/{uid}: true
                    DataSnapshot participantsSnapshot = chatSnapshot.child("participants");
                    if (participantsSnapshot.exists() && participantsSnapshot.hasChild(currentUserId)) {
                        isParticipant = true;
                    }

                    // Option 2: user1Id / user2Id (from your other code)
                    String user1Id = chatSnapshot.child("user1Id").getValue(String.class);
                    String user2Id = chatSnapshot.child("user2Id").getValue(String.class);
                    if (!isParticipant) {
                        if (currentUserId.equals(user1Id) || currentUserId.equals(user2Id)) {
                            isParticipant = true;
                        }
                    }

                    if (!isParticipant) continue;

                    // ----- Build ChatRoom from snapshot -----
                    ChatRoom room = new ChatRoom();
                    room.setChatId(chatId);

                    String lastMessage = chatSnapshot.child("lastMessage").getValue(String.class);
                    Long lastMessageTime = chatSnapshot.child("lastMessageTime").getValue(Long.class);
                    String lastMessageSender = chatSnapshot.child("lastMessageSender").getValue(String.class);
                    String productId = chatSnapshot.child("productId").getValue(String.class);

                    room.setLastMessage(lastMessage != null ? lastMessage : "Start a conversation");
                    room.setLastMessageTime(lastMessageTime != null ? lastMessageTime : 0L);
                    room.setLastMessageSender(lastMessageSender != null ? lastMessageSender : "");
                    room.setProductId(productId != null ? productId : "");

                    // ----- Determine other user (from user1/user2 or participants) -----
                    String otherUserId = null;
                    String otherUserName = null;

                    String user1Name = chatSnapshot.child("user1Name").getValue(String.class);
                    String user2Name = chatSnapshot.child("user2Name").getValue(String.class);

                    if (currentUserId.equals(user1Id)) {
                        otherUserId = user2Id;
                        otherUserName = user2Name;
                    } else if (currentUserId.equals(user2Id)) {
                        otherUserId = user1Id;
                        otherUserName = user1Name;
                    } else if (participantsSnapshot.exists()) {
                        for (DataSnapshot participant : participantsSnapshot.getChildren()) {
                            String pid = participant.getKey();
                            if (pid != null && !pid.equals(currentUserId)) {
                                otherUserId = pid;
                                break;
                            }
                        }
                    }

                    room.setOtherUserId(otherUserId);

                    if (otherUserName != null && !otherUserName.isEmpty()) {
                        room.setOtherUserName(otherUserName);
                    }

                    chatRooms.add(room);

                    // If we still don't know name, fetch from /users
                    if (otherUserId != null && (otherUserName == null || otherUserName.isEmpty())) {
                        loadOtherUserInfo(room, otherUserId);
                    }
                }

                sortChatRooms();
                chatListAdapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error if needed
            }
        };

        chatsRef.addValueEventListener(chatListener);
    }

    /**
     * Just updates the name/avatar of an existing ChatRoom, does NOT add duplicates.
     */
    private void loadOtherUserInfo(ChatRoom room, String otherUserId) {
        usersRef.child(otherUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String otherUserName = snapshot.child("fullName").getValue(String.class);
                String otherUserAvatar = snapshot.child("profileImage").getValue(String.class);

                String finalName = (otherUserName != null && !otherUserName.isEmpty())
                        ? otherUserName
                        : "User";

                // Find the same room in the list and update it
                for (int i = 0; i < chatRooms.size(); i++) {
                    ChatRoom existing = chatRooms.get(i);
                    if (existing.getChatId().equals(room.getChatId())) {
                        existing.setOtherUserName(finalName);
                        existing.setOtherUserAvatar(otherUserAvatar);
                        break;
                    }
                }

                chatListAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error if needed
            }
        });
    }

    private void sortChatRooms() {
        Collections.sort(chatRooms, (r1, r2) ->
                Long.compare(r2.getLastMessageTime(), r1.getLastMessageTime()));
    }

    private void updateEmptyState() {
        if (chatRooms.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvChatList.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvChatList.setVisibility(View.VISIBLE);
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
    }

    private void onChatRoomClick(ChatRoom chatRoom) {
        Intent intent = new Intent(this, activity_chat_owner.class);
        intent.putExtra("chatId", chatRoom.getChatId());
        intent.putExtra("ownerId", chatRoom.getOtherUserId());
        intent.putExtra("productId", chatRoom.getProductId());
        startActivity(intent);
    }
}
