package com.example.modestyrent_app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class activity_chat_owner extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1001;
    private static final String TAG = "ChatOwnerActivity";

    private RecyclerView rvChatMessages;
    private TextInputEditText etMessage;
    private FloatingActionButton btnSend;
    private ImageView backButton, btnAttachLeft;
    private TextView tvOwnerName;
    private ProgressBar progressBar;

    private ChatAdapter chatAdapter;
    private final List<ChatMessage> chatMessages = new ArrayList<>();

    private String chatId, ownerId, productId;
    private String currentUserId;
    private DatabaseReference chatRoomRef, messagesRef, usersRef, userUnreadRef;
    private StorageReference storageRef;

    private ChildEventListener messagesListener;
    private ValueEventListener chatListener;
    private UnreadCounterManager unreadCounterManager;

    private boolean isMarkingAsRead = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_owner);

        // 🔒 AUTH GUARD
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        initializeViews();
        getIntentData();
        initializeFirebase();
        setupRecyclerView();
        setupClickListeners();
        loadOwnerInfo();
        initializeChatRoom();

        // Initialize unread counter manager
        unreadCounterManager = UnreadCounterManager.getInstance(this);

        // Mark chat as read when opened
        markChatAsRead();

        // Then load messages
        loadChatMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        markChatAsRead();
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
        if (messagesRef != null && messagesListener != null) {
            messagesRef.removeEventListener(messagesListener);
        }
        if (chatRoomRef != null && chatListener != null) {
            chatRoomRef.removeEventListener(chatListener);
        }
        unreadCounterManager.removeListener();
    }

    private void initializeViews() {
        rvChatMessages = findViewById(R.id.rvChatMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        backButton = findViewById(R.id.backButton);
        btnAttachLeft = findViewById(R.id.btnAttachLeft);
        tvOwnerName = findViewById(R.id.tvOwnerName);
        progressBar = findViewById(R.id.progressBar);
    }

    private void getIntentData() {
        chatId = getIntent().getStringExtra("chatId");
        ownerId = getIntent().getStringExtra("ownerId");
        productId = getIntent().getStringExtra("productId");

        Log.d(TAG, "Chat ID: " + chatId);
        Log.d(TAG, "Owner ID: " + ownerId);
        Log.d(TAG, "Product ID: " + productId);

        if (chatId == null || ownerId == null) {
            Toast.makeText(this, "Error: Missing chat information", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeFirebase() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();
        Log.d(TAG, "Current User ID: " + currentUserId);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        chatRoomRef = database.getReference("chats").child(chatId);
        messagesRef = chatRoomRef.child("messages");
        usersRef = database.getReference("users");
        userUnreadRef = database.getReference("userUnreadCounts").child(currentUserId);
        storageRef = FirebaseStorage.getInstance().getReference("chat_files");
    }

    private void initializeChatRoom() {
        chatRoomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Create new chat
                    Map<String, Object> chatRoomData = new HashMap<>();
                    Map<String, Object> participants = new HashMap<>();
                    participants.put(currentUserId, true);
                    participants.put(ownerId, true);

                    chatRoomData.put("participants", participants);
                    chatRoomData.put("createdAt", System.currentTimeMillis());
                    chatRoomData.put("productId", productId);
                    chatRoomData.put("lastMessage", "");
                    chatRoomData.put("lastMessageTime", 0L);
                    chatRoomData.put("lastMessageSender", "");

                    // Initialize lastSeenBy
                    Map<String, Object> lastSeenBy = new HashMap<>();
                    lastSeenBy.put(currentUserId, System.currentTimeMillis());
                    lastSeenBy.put(ownerId, 0L);
                    chatRoomData.put("lastSeenBy", lastSeenBy);

                    chatRoomRef.setValue(chatRoomData)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat room created"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to create chat room: " + e.getMessage()));
                } else {
                    // Chat exists → ensure participants map and productId are set
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("participants/" + currentUserId, true);
                    updates.put("participants/" + ownerId, true);

                    // Initialize lastSeenBy if it doesn't exist
                    if (!snapshot.hasChild("lastSeenBy")) {
                        updates.put("lastSeenBy/" + currentUserId, System.currentTimeMillis());
                        updates.put("lastSeenBy/" + ownerId, 0L);
                    }

                    if (!snapshot.hasChild("productId") && productId != null) {
                        updates.put("productId", productId);
                    }

                    chatRoomRef.updateChildren(updates)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat room participants ensured"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to ensure participants: " + e.getMessage()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check chat room: " + error.getMessage());
            }
        });
    }

    /**
     * Mark all messages in this chat as read for current user
     */
    private void markChatAsRead() {
        if (currentUserId == null || chatId == null || isMarkingAsRead) return;

        isMarkingAsRead = true;
        Log.d(TAG, "Marking chat as read: " + chatId);

        // Update last seen time
        DatabaseReference lastSeenRef = chatRoomRef.child("lastSeenBy").child(currentUserId);
        long currentTime = System.currentTimeMillis();
        lastSeenRef.setValue(currentTime)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Updated lastSeen time: " + currentTime);

                    // Mark all unread messages as read
                    markAllMessagesAsRead();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update lastSeen time: " + e.getMessage());
                    isMarkingAsRead = false;
                });
    }

    private void markAllMessagesAsRead() {
        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    isMarkingAsRead = false;
                    return;
                }

                int unreadCount = 0;
                int markedCount = 0;

                for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                    String messageId = messageSnapshot.getKey();
                    String senderId = messageSnapshot.child("senderId").getValue(String.class);
                    Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);

                    if (messageId != null && senderId != null && timestamp != null) {
                        // Only mark messages from other user that are unread
                        if (!senderId.equals(currentUserId)) {
                            DataSnapshot readBySnapshot = messageSnapshot.child("readBy").child(currentUserId);
                            if (!readBySnapshot.exists() || !Boolean.TRUE.equals(readBySnapshot.getValue(Boolean.class))) {
                                unreadCount++;
                                // Mark as read
                                DatabaseReference readByRef = messagesRef
                                        .child(messageId)
                                        .child("readBy")
                                        .child(currentUserId);
                                readByRef.setValue(true);
                                markedCount++;
                            }
                        }
                    }
                }

                Log.d(TAG, "Marked " + markedCount + " messages as read, total unread was: " + unreadCount);

                // Update global unread count
                if (unreadCount > 0) {
                    unreadCounterManager.decrementUnreadCount(unreadCount);
                }

                isMarkingAsRead = false;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to mark messages as read: " + error.getMessage());
                isMarkingAsRead = false;
            }
        });
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(chatMessages, currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChatMessages.setLayoutManager(layoutManager);
        rvChatMessages.setAdapter(chatAdapter);
        rvChatMessages.setHasFixedSize(false);
        rvChatMessages.setNestedScrollingEnabled(true);
    }

    private void loadChatMessages() {
        Log.d(TAG, "Loading messages for chat: " + chatId);

        // Clear existing messages
        chatMessages.clear();
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }

        // Remove previous listener
        if (messagesListener != null) {
            messagesRef.removeEventListener(messagesListener);
        }

        // Load existing messages
        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "No messages found");
                    return;
                }

                for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                    ChatMessage message = messageSnapshot.getValue(ChatMessage.class);
                    if (message != null) {
                        chatMessages.add(message);
                        Log.d(TAG, "Loaded message: " + message.getMessageId() + " - " + message.getMessage());
                    }
                }

                Log.d(TAG, "Total messages loaded: " + chatMessages.size());
                chatAdapter.notifyDataSetChanged();

                if (!chatMessages.isEmpty()) {
                    rvChatMessages.post(() -> rvChatMessages.scrollToPosition(chatMessages.size() - 1));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load messages: " + error.getMessage());
            }
        });

        // Listen for new messages
        messagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                ChatMessage message = snapshot.getValue(ChatMessage.class);
                if (message != null && !messageExists(message.getMessageId())) {
                    chatMessages.add(message);
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);

                    // Auto-scroll to new message
                    rvChatMessages.post(() -> {
                        rvChatMessages.scrollToPosition(chatMessages.size() - 1);
                    });

                    Log.d(TAG, "New message added: " + message.getMessageId() + " from " + message.getSenderId());

                    // If message is from other user and not read yet, increment unread
                    if (!message.getSenderId().equals(currentUserId) &&
                            (message.getReadBy() == null || !message.getReadBy().containsKey(currentUserId))) {
                        Log.d(TAG, "New unread message detected");
                        unreadCounterManager.incrementUnreadCount();

                        // Mark as read if user is currently viewing the chat
                        DatabaseReference readByRef = messagesRef
                                .child(message.getMessageId())
                                .child("readBy")
                                .child(currentUserId);
                        readByRef.setValue(true);
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Handle message updates (e.g., read status)
                ChatMessage updatedMessage = snapshot.getValue(ChatMessage.class);
                if (updatedMessage != null) {
                    for (int i = 0; i < chatMessages.size(); i++) {
                        ChatMessage existing = chatMessages.get(i);
                        if (existing.getMessageId().equals(updatedMessage.getMessageId())) {
                            chatMessages.set(i, updatedMessage);
                            chatAdapter.notifyItemChanged(i);
                            break;
                        }
                    }
                }
            }

            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to listen for new messages: " + error.getMessage());
            }
        };

        messagesRef.addChildEventListener(messagesListener);
    }

    private boolean messageExists(String messageId) {
        if (messageId == null) return false;
        for (ChatMessage message : chatMessages) {
            if (message.getMessageId() != null && message.getMessageId().equals(messageId)) {
                return true;
            }
        }
        return false;
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        btnAttachLeft.setOnClickListener(v -> openImagePicker());

        btnSend.setOnClickListener(v -> sendTextMessage());

        etMessage.setOnClickListener(v -> {
            if (!chatMessages.isEmpty()) {
                rvChatMessages.smoothScrollToPosition(chatMessages.size() - 1);
            }
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        Intent chooser = Intent.createChooser(intent, "Select Image to Send");
        startActivityForResult(chooser, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                Log.d(TAG, "Image selected: " + fileUri);
                uploadImage(fileUri);
            } else {
                Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void uploadImage(Uri imageUri) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Uploading Image");
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String fileName = "img_" + System.currentTimeMillis();
        StorageReference fileRef = storageRef.child(chatId).child(fileName + ".jpg");

        fileRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot ->
                        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            progressDialog.dismiss();
                            String downloadUrl = uri.toString();
                            Log.d(TAG, "Image uploaded: " + downloadUrl);
                            sendImageMessage(downloadUrl);
                        }).addOnFailureListener(e -> {
                            progressDialog.dismiss();
                            Log.e(TAG, "Failed to get download URL: " + e.getMessage());
                            Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                        })
                )
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Log.e(TAG, "Upload failed: " + e.getMessage());
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                })
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    progressDialog.setMessage("Uploaded " + (int) progress + "%");
                });
    }

    private void sendImageMessage(String imageUrl) {
        String messageId = messagesRef.push().getKey();
        if (messageId == null) {
            Toast.makeText(this, "Failed to send image", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setSenderId(currentUserId);
        message.setFileUrl(imageUrl);
        message.setFileType("image");
        message.setTimestamp(System.currentTimeMillis());
        message.setType("image");
        message.setMessage("");

        // Initialize readBy with current user (sender)
        Map<String, Boolean> readBy = new HashMap<>();
        readBy.put(currentUserId, true);
        message.setReadBy(readBy);

        messagesRef.child(messageId).setValue(message)
                .addOnSuccessListener(aVoid -> {
                    updateLastMessage("[Image]");
                    Log.d(TAG, "Image message sent: " + messageId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to send image message: " + e.getMessage());
                });
    }

    private void sendTextMessage() {
        String messageText = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(messageText)) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageId = messagesRef.push().getKey();
        if (messageId == null) {
            Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setSenderId(currentUserId);
        message.setMessage(messageText);
        message.setTimestamp(System.currentTimeMillis());
        message.setType("text");
        message.setFileUrl(null);
        message.setFileType(null);

        // Initialize readBy with current user (sender)
        Map<String, Boolean> readBy = new HashMap<>();
        readBy.put(currentUserId, true);
        message.setReadBy(readBy);

        messagesRef.child(messageId).setValue(message)
                .addOnSuccessListener(aVoid -> {
                    etMessage.setText("");
                    updateLastMessage(messageText);
                    Log.d(TAG, "Text message sent: " + messageId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to send message: " + e.getMessage());
                });
    }

    private void updateLastMessage(String lastMessageRaw) {
        String preview = lastMessageRaw;
        if (preview != null && preview.length() > 60) {
            preview = preview.substring(0, 57) + "...";
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", preview != null ? preview : "");
        updates.put("lastMessageTime", System.currentTimeMillis());
        updates.put("lastMessageSender", currentUserId);

        chatRoomRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Last message updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update last message: " + e.getMessage()));
    }

    private void loadOwnerInfo() {
        usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String ownerName = snapshot.child("fullName").getValue(String.class);
                    if (ownerName != null && !ownerName.isEmpty()) {
                        tvOwnerName.setText(ownerName);
                    } else {
                        tvOwnerName.setText("Owner");
                    }
                } else {
                    tvOwnerName.setText("Owner");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_chat_owner.this, "Failed to load owner info", Toast.LENGTH_SHORT).show();
            }
        });
    }
}