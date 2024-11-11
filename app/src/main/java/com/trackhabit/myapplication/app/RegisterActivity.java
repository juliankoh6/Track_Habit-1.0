package com.trackhabit.myapplication.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText nameField, emailField, passwordField, confirmPasswordField;
    private Button registerButton, backToLoginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize fields
        nameField = findViewById(R.id.registerNameField);
        emailField = findViewById(R.id.registerEmailField);
        passwordField = findViewById(R.id.registerPasswordField);
        confirmPasswordField = findViewById(R.id.confirmPasswordField);
        registerButton = findViewById(R.id.completeRegisterButton);
        backToLoginButton = findViewById(R.id.backToLoginButton);

        // Handle registration logic
        registerButton.setOnClickListener(v -> {
            String name = nameField.getText().toString().trim();
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();
            String confirmPassword = confirmPasswordField.getText().toString().trim();

            // Check if any fields are empty and that email is in correct format
            if (name.isEmpty()) {
                showToast("Name is required");
                nameField.requestFocus();
                return;
            }

            if (email.isEmpty()) {
                showToast("Email is required");
                emailField.requestFocus();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showToast("Please enter a valid email");
                emailField.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                showToast("Password is required");
                passwordField.requestFocus();
                return;
            }

            if (confirmPassword.isEmpty()) {
                showToast("Please confirm your password");
                confirmPasswordField.requestFocus();
                return;
            }

            if (!password.equals(confirmPassword)) {
                showToast("Passwords do not match");
                return;
            }

            // Register user with Firebase
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                //  Update the Firebase Auth
                                user.updateProfile(new UserProfileChangeRequest.Builder()
                                                .setDisplayName(name)
                                                .build())
                                        .addOnCompleteListener(profileUpdateTask -> {
                                            if (profileUpdateTask.isSuccessful()) {
                                                // Also update the display name in Firestore
                                                Map<String, Object> profileUpdates = new HashMap<>();
                                                profileUpdates.put("displayName", name);
                                                profileUpdates.put("darkThemeEnabled", false);
                                                db.collection("users").document(user.getUid())
                                                        .set(profileUpdates, SetOptions.merge())
                                                        .addOnSuccessListener(aVoid -> {
                                                            // Redirect to home activity
                                                            Intent intent = new Intent(RegisterActivity.this, HomeActivity.class);
                                                            startActivity(intent);
                                                            finish();
                                                        })
                                                        .addOnFailureListener(e ->
                                                                Toast.makeText(RegisterActivity.this, "Failed to update Firestore", Toast.LENGTH_SHORT).show()
                                                        );
                                            } else {
                                                Toast.makeText(RegisterActivity.this, "Failed to update Auth profile", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                        } else {
                            Toast.makeText(RegisterActivity.this, "Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });

        });

        // Redirect user back to Login page
        backToLoginButton.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void showToast(String message) {
        Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
    }
}
