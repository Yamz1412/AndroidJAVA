package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.getInstance(this).applyTheme(this);
        super.onCreate(savedInstanceState);
        ThemeManager.getInstance(this).applySystemColorsToWindow(this);
        enforceAuthentication();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.getInstance(this).applySystemColorsToWindow(this);
    }

    private void enforceAuthentication() {
        AuthManager authManager = AuthManager.getInstance();
        if (authManager.getCurrentUser() == null) {
            Intent intent = new Intent(this, SignInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}