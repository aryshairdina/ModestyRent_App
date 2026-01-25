package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class activity_chat_list extends AppCompatActivity implements UnreadCounterManager.OnUnreadCountChangeListener {

    private static final String TAG = "ChatListActivity";

    private RecyclerView rvChatList;
    private LinearLayout emptyState;
    private ImageView backButton;
    private TextView tvUnreadHeader;

    private ChatListAdapter chatListAdapter;
    private final List<ChatRoom> chatRooms = new ArrayList<>();
    private final Map<String, ChatRoom> chatRoomMap = new HashMap<>(); // For quick lookup

    private DatabaseReference chatsRef, usersRef;
    private String currentUserId;

    private ValueEventListener chatListener;
    private UnreadCounterManager unreadCounterManager;
    private int totalUnreadCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        // 🔒 AUTH GUARD
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        initializeViews();
        initializeFirebase();
        setupRecyclerView();
        setupClickListeners();

        // Initialize unread counter manager
        unreadCounterManager = UnreadCounterManager.getInstance(this);
        unreadCounterManager.setListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadChatRooms();
        unreadCounterManager.startListening();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unreadCounterManager.stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatsRef != null && chatListener != null) {
            chatsRef.removeEventListener(chatListener);
        }
        unreadCounterManager.removeListener();
    }

    @Override
    public void onUnreadCountChanged(int totalCount) {
        this.totalUnreadCount = totalCount;
        updateHeaderCounter();
    }

    private void initializeViews() {
        rvChatList = findViewById(R.id.rvChatList);
        emptyState = findViewById(R.id.emptyState);
        backButton = findViewById(R.id.backButton);
        tvUnreadHeader = findViewById(R.id.tvUnreadHeader);
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

    private void loadChatRooms() {
        if (chatListener != null) {
            chatsRef.removeEventListener(chatListener);
        }

        chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chatRooms.clear();
                chatRoomMap.clear();
                totalUnreadCount = 0;

                Log.d(TAG, "Loading chats for user: " + currentUserId);

                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId == null) continue;

                    // Check if current user is participant
                    boolean isParticipant = false;
                    DataSnapshot participantsSnapshot = chatSnapshot.child("participants");

                    if (participantsSnapshot.exists() && participantsSnapshot.hasChild(currentUserId)) {
                        isParticipant = true;
                        Log.d(TAG, "User is participant in chat: " + chatId);
                    }

                    String user1Id = chatSnapshot.child("user1Id").getValue(String.class);
                    String user2Id = chatSnapshot.child("user2Id").getValue(String.class);

                    if (!isParticipant) {
                        if (currentUserId.equals(user1Id) || currentUserId.equals(user2Id)) {
                            isParticipant = true;
                            Log.d(TAG, "User found via user1/user2 fields in chat: " + chatId);
                        }
                    }

                    if (!isParticipant) {
                        Log.d(TAG, "User is NOT participant in chat: " + chatId);
                        continue;
                    }

                    // Get last seen time for current user
                    long lastSeenTime = 0;
                    DataSnapshot lastSeenSnapshot = chatSnapshot.child("lastSeenBy").child(currentUserId);
                    if (lastSeenSnapshot.exists()) {
                        Long lastSeen = lastSeenSnapshot.getValue(Long.class);
                        if (lastSeen != null) {
                            lastSeenTime = lastSeen;
                            Log.d(TAG, "Last seen time for chat " + chatId + ": " + lastSeenTime);
                        }
                    } else {
                        Log.d(TAG, "No lastSeenBy found for user in chat: " + chatId);
                    }

                    // Calculate unread messages for this chat
                    int chatUnreadCount = 0;
                    DataSnapshot messagesSnapshot = chatSnapshot.child("messages");
                    if (messagesSnapshot.exists()) {
                        for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
                            Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);
                            String senderId = messageSnapshot.child("senderId").getValue(String.class);

                            if (timestamp != null && senderId != null) {
                                // Message is unread if:
                                // 1. It's newer than last seen time
                                // 2. It's not sent by current user
                                // 3. It hasn't been read by current user
                                if (timestamp > lastSeenTime && !senderId.equals(currentUserId)) {
                                    // Check if message has readBy field
                                    DataSnapshot readBySnapshot = messageSnapshot.child("readBy").child(currentUserId);
                                    if (!readBySnapshot.exists() || !Boolean.TRUE.equals(readBySnapshot.getValue(Boolean.class))) {
                                        chatUnreadCount++;
                                    }
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Chat " + chatId + " has " + chatUnreadCount + " unread messages");

                    // Update total unread count
                    totalUnreadCount += chatUnreadCount;

                    // Build ChatRoom object
                    ChatRoom room = new ChatRoom();
                    room.setChatId(chatId);
                    room.setUnreadCount(chatUnreadCount);

                    String lastMessage = chatSnapshot.child("lastMessage").getValue(String.class);
                    Long lastMessageTime = chatSnapshot.child("lastMessageTime").getValue(Long.class);
                    String lastMessageSender = chatSnapshot.child("lastMessageSender").getValue(String.class);
                    String productId = chatSnapshot.child("productId").getValue(String.class);

                    room.setLastMessage(lastMessage != null ? lastMessage : "Start a conversation");
                    room.setLastMessageTime(lastMessageTime != null ? lastMessageTime : 0L);
                    room.setLastMessageSender(lastMessageSender != null ? lastMessageSender : "");
                    room.setProductId(productId != null ? productId : "");

                    // Determine other user
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
                    chatRoomMap.put(chatId, room);

                    // Fetch user info if needed
                    if (otherUserId != null && (otherUserName == null || otherUserName.isEmpty())) {
                        loadOtherUserInfo(room, otherUserId);
                    }
                }

                Log.d(TAG, "Total unread count calculated: " + totalUnreadCount);

                // Update global unread count in database
                updateGlobalUnreadCount(totalUnreadCount);

                sortChatRooms();
                chatListAdapter.notifyDataSetChanged();
                updateEmptyState();
                updateHeaderCounter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load chats: " + error.getMessage());
            }
        };

        chatsRef.addValueEventListener(chatListener);
    }

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

                // Update the room in the list
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
                // Handle error
            }
        });
    }

    private void sortChatRooms() {
        Collections.sort(chatRooms, (r1, r2) ->
                Long.compare(r2.getLastMessageTime(), r1.getLastMessageTime()));
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
        Log.d(TAG, "Opening chat: " + chatRoom.getChatId());

        // Update last seen time when opening chat
        updateLastSeenTime(chatRoom.getChatId());

        // Clear this chat's unread count immediately
        chatRoom.setUnreadCount(0);
        chatListAdapter.notifyDataSetChanged();

        Intent intent = new Intent(this, activity_chat_owner.class);
        intent.putExtra("chatId", chatRoom.getChatId());
        intent.putExtra("ownerId", chatRoom.getOtherUserId());
        intent.putExtra("productId", chatRoom.getProductId());
        startActivity(intent);
    }

    private void updateLastSeenTime(String chatId) {
        if (currentUserId == null || chatId == null) return;

        DatabaseReference lastSeenRef = chatsRef.child(chatId).child("lastSeenBy").child(currentUserId);
        long currentTime = System.currentTimeMillis();
        lastSeenRef.setValue(currentTime)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Updated lastSeen time for chat " + chatId + ": " + currentTime);

                    // Also mark all messages as read
                    markAllMessagesAsRead(chatId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update lastSeen time: " + e.getMessage());
                });
    }

    private void markAllMessagesAsRead(String chatId) {
        if (currentUserId == null || chatId == null) return;

        DatabaseReference chatRef = chatsRef.child(chatId);
        chatRef.child("messages").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                int markedCount = 0;
                for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                    String messageId = messageSnapshot.getKey();
                    String senderId = messageSnapshot.child("senderId").getValue(String.class);

                    if (messageId != null && senderId != null && !senderId.equals(currentUserId)) {
                        DatabaseReference readByRef = chatRef
                                .child("messages")
                                .child(messageId)
                                .child("readBy")
                                .child(currentUserId);
                        readByRef.setValue(true);
                        markedCount++;
                    }
                }
                Log.d(TAG, "Marked " + markedCount + " messages as read in chat " + chatId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to mark messages as read: " + error.getMessage());
            }
        });
    }

    private void updateGlobalUnreadCount(int count) {
        if (currentUserId == null) return;

        DatabaseReference userUnreadRef = FirebaseDatabase.getInstance()
                .getReference("userUnreadCounts")
                .child(currentUserId);
        userUnreadRef.setValue(count)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Updated global unread count: " + count))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update global unread count: " + e.getMessage()));
    }

    private void updateHeaderCounter() {
        runOnUiThread(() -> {
            if (tvUnreadHeader != null) {
                String title = "Messages";
                if (totalUnreadCount > 0) {
                    if (totalUnreadCount > 99) {
                        title += " (99+)";
                    } else {
                        title += " (" + totalUnreadCount + ")";
                    }
                }
                tvUnreadHeader.setText(title);
                Log.d(TAG, "Header updated: " + title);
            }
        });
    }
}