package com.trackhabit.myapplication.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText editDisplayName;
    private SwitchCompat themeSwitch;
    private Button saveNameButton, logoutButton;
    private boolean isDarkTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Load theme preference from Firestore and set content view
        loadThemePreference();
        setContentView(R.layout.activity_profile);

        // Initialize UI elements
        editDisplayName = findViewById(R.id.editDisplayName);
        themeSwitch = findViewById(R.id.themeSwitch);
        saveNameButton = findViewById(R.id.saveNameButton);
        logoutButton = findViewById(R.id.logoutButton);

        // Set up button listeners
        saveNameButton.setOnClickListener(v -> saveDisplayName());
        logoutButton.setOnClickListener(v -> logoutUser());

        // Navigation buttons
        findViewById(R.id.homeButton).setOnClickListener(v -> startActivity(new Intent(this, HomeActivity.class)));
        findViewById(R.id.progressButton).setOnClickListener(v -> startActivity(new Intent(this, ProgressActivity.class)));
        findViewById(R.id.profileButton).setOnClickListener(v -> Toast.makeText(this, "Already on profile", Toast.LENGTH_SHORT).show());
    }

    // Check if theme switch is on and apply
    private void setupThemeSwitchListener() {
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != isDarkTheme) {
                saveThemePreference(isChecked);
            }
        });
    }

    // Retrieve theme preference from Firestore and apply it
    private void loadThemePreference() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();

            // Retrieving from Firestore
            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        //  light theme if no preference is found
                        isDarkTheme = documentSnapshot.contains("darkThemeEnabled") &&
                                documentSnapshot.getBoolean("darkThemeEnabled");

                        setAppTheme(isDarkTheme);

                        // Initialize switch without triggering listener
                        themeSwitch.setOnCheckedChangeListener(null);
                        themeSwitch.setChecked(isDarkTheme);
                        // Reattach listener after setting the switch
                        setupThemeSwitchListener();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load theme preference",
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // Apply the theme
    private void setAppTheme(boolean isDarkTheme) {
        AppCompatDelegate.setDefaultNightMode(
                isDarkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    // Save theme preference to Firestore when changed
    private void saveThemePreference(boolean isDarkTheme) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            this.isDarkTheme = isDarkTheme;

            // Prepare preference data
            Map<String, Object> themePreference = new HashMap<>();
            themePreference.put("darkThemeEnabled", isDarkTheme);

            // Save the theme preference to Firestore
            db.collection("users").document(userId)
                    .set(themePreference, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> setAppTheme(isDarkTheme))
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save theme preference", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // Handle configuration change
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadThemePreference();
    }

    // Updates new display name to Firestore
    private void saveDisplayName() {
        // Get the entered name and trim spaces
        String newName = editDisplayName.getText().toString().trim();

        // Ensure the name is not empty
        if (newName.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if a user is logged in, then update Firestore directly
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .update("displayName", newName)
                    .addOnSuccessListener(aVoid ->
                            Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show()
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
                    );
        }
    }

    // Log out the user
    private void logoutUser() {
        mAuth.signOut();

        // Return to default light theme for LoginActivity
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
