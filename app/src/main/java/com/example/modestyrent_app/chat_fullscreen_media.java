package com.example.modestyrent_app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

public class chat_fullscreen_media extends Dialog {

    public chat_fullscreen_media(@NonNull Context context, String imageUrl) {
        super(context);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_fullscreen_media);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
        }

        ImageView fullscreenImage = findViewById(R.id.fullscreenImage);
        ImageView btnClose = findViewById(R.id.btnClose);

        fullscreenImage.setVisibility(ImageView.VISIBLE);

        Glide.with(context)
                .load(imageUrl)
                .into(fullscreenImage);

        btnClose.setOnClickListener(v -> dismiss());
    }
}
