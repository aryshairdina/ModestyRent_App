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

public class activity_chat_list extends AppCompatActivity {

    private RecyclerView rvChatList;
    private LinearLayout emptyState;
    private ImageView backButton;

    private ChatListAdapter chatListAdapter;
    private List<ChatRoom> chatRooms;

    private DatabaseReference chatsRef, usersRef;
    private String currentUserId;

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
        chatRooms = new ArrayList<>();
        chatListAdapter = new ChatListAdapter(chatRooms, this::onChatRoomClick);
        rvChatList.setLayoutManager(new LinearLayoutManager(this));
        rvChatList.setAdapter(chatListAdapter);
    }

    private void loadChatRooms() {
        chatsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chatRooms.clear();
                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();

                    // Check if current user is participant in this chat
                    DataSnapshot participantsSnapshot = chatSnapshot.child("participants");
                    if (participantsSnapshot.hasChild(currentUserId)) {
                        ChatRoom chatRoom = new ChatRoom();
                        chatRoom.setChatId(chatId);

                        // Get chat data
                        String lastMessage = chatSnapshot.child("lastMessage").getValue(String.class);
                        Long lastMessageTime = chatSnapshot.child("lastMessageTime").getValue(Long.class);
                        String lastMessageSender = chatSnapshot.child("lastMessageSender").getValue(String.class);
                        String productId = chatSnapshot.child("productId").getValue(String.class);

                        chatRoom.setLastMessage(lastMessage != null ? lastMessage : "Start a conversation");
                        chatRoom.setLastMessageTime(lastMessageTime != null ? lastMessageTime : 0);
                        chatRoom.setLastMessageSender(lastMessageSender != null ? lastMessageSender : "");
                        chatRoom.setProductId(productId != null ? productId : "");

                        // Find the other participant
                        for (DataSnapshot participant : participantsSnapshot.getChildren()) {
                            String participantId = participant.getKey();
                            if (participantId != null && !participantId.equals(currentUserId)) {
                                chatRoom.setOtherUserId(participantId);
                                loadOtherUserInfo(chatRoom, participantId);
                                break;
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    private void loadOtherUserInfo(ChatRoom chatRoom, String otherUserId) {
        usersRef.child(otherUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String otherUserName = snapshot.child("fullName").getValue(String.class);
                    String otherUserAvatar = snapshot.child("profileImage").getValue(String.class);

                    chatRoom.setOtherUserName(otherUserName != null ? otherUserName : "User");
                    chatRoom.setOtherUserAvatar(otherUserAvatar);

                    // Check if chat room already exists to avoid duplicates
                    boolean exists = false;
                    for (ChatRoom existingRoom : chatRooms) {
                        if (existingRoom.getChatId().equals(chatRoom.getChatId())) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        chatRooms.add(chatRoom);
                        sortChatRooms();
                        chatListAdapter.notifyDataSetChanged();
                        updateEmptyState();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    private void sortChatRooms() {
        Collections.sort(chatRooms, (r1, r2) -> Long.compare(r2.getLastMessageTime(), r1.getLastMessageTime()));
    }

    private void updateEmptyState() {
        runOnUiThread(() -> {
            if (chatRooms.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                rvChatList.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                rvChatList.setVisibility(View.VISIBLE);
            }
        });
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