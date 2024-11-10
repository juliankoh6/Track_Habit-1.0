package com.trackhabit.myapplication.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class HomeActivity extends AppCompatActivity implements HabitAdapter.OnHabitCompleteListener, HabitAdapter.OnHabitEditListener  {

    private static final String LAST_REFRESH_DATE_KEY = "last_refresh_date";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView welcomeText;
    private TextView dateText;
    private ProgressBar progressBar;
    private ArrayList<Habit> habitList = new ArrayList<>();
    private HabitAdapter habitAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        welcomeText = findViewById(R.id.welcomeText);
        dateText = findViewById(R.id.dateText);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        RecyclerView habitRecyclerView = findViewById(R.id.habitRecyclerView);
        habitRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set up adapter and attach listener to update progress on habit completion change
        habitAdapter = new HabitAdapter(habitList, this, this);
        habitRecyclerView.setAdapter(habitAdapter);

        // Display Title
        updateWelcomeText();

        // Load habits and set theme
        applyThemeFromFirestore();
        loadHabits();

        // Set today's date
        setDateText();

        // Button to add a habit
        Button addHabitButton = findViewById(R.id.addHabitButton);
        addHabitButton.setOnClickListener(view -> showAddHabitDialog());

        // Button to delete habits
        Button deleteHabitButton = findViewById(R.id.deleteHabitButton);
        deleteHabitButton.setOnClickListener(view -> showDeleteHabitDialog());

        // Navigation buttons
        findViewById(R.id.homeButton).setOnClickListener(v -> Toast.makeText(this, "Already on Home", Toast.LENGTH_SHORT).show());
        findViewById(R.id.progressButton).setOnClickListener(v -> startActivity(new Intent(this, ProgressActivity.class)));
        findViewById(R.id.profileButton).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        // Schedule daily habit reset at midnight
        scheduleDailyReset();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update the welcome text in case of changes
        updateWelcomeText();

        // Check if the date has changed for daily refresh
        if (isNewDay()) {
            loadHabits();
            Toast.makeText(this, "Daily reset applied", Toast.LENGTH_SHORT).show();
        }
    }

    // Method to format and display today's date
    private void setDateText() {
        // Get today's date
        LocalDate today = LocalDate.now();

        // Format date and display
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d - MMMM - yyyy", Locale.getDefault());
        String formattedDate = today.format(formatter);
        dateText.setText(formattedDate);
    }

    // Method to update the Checklist Title based on username of current user
    private void updateWelcomeText() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String updatedDisplayName = documentSnapshot.getString("displayName");
                            if (updatedDisplayName != null) {
                                welcomeText.setText(updatedDisplayName + "'s habits");
                            }
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(HomeActivity.this, "Failed to load user name",
                                    Toast.LENGTH_SHORT).show());
        }
    }

    // Apply theme preference from Firestore and set the theme
    private void applyThemeFromFirestore() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.contains("darkThemeEnabled")) {
                            boolean darkThemeEnabled = documentSnapshot.getBoolean("darkThemeEnabled");
                            //  apply theme
                            AppCompatDelegate.setDefaultNightMode(darkThemeEnabled ?
                                    AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                        }
                    });
        }
    }

    // Checks if the current day is different from the last saved refresh date
    private boolean isNewDay() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Date todayDate = today.getTime();

        // Retrieve the last saved refresh date
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        long lastRefreshTime = preferences.getLong(LAST_REFRESH_DATE_KEY, 0);
        Date lastRefreshDate = new Date(lastRefreshTime);

        if (!lastRefreshDate.equals(todayDate)) {
            preferences.edit().putLong(LAST_REFRESH_DATE_KEY, todayDate.getTime()).apply();
            return true;
        }
        return false;
    }

    // Load habits from Firestore
    private void loadHabits() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            db.collection("users").document(userId).collection("habits")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        habitList.clear();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Habit habit = doc.toObject(Habit.class);
                            habit.setId(doc.getId());
                            habit.setUserId(userId);

                            // update dailyCompletions
                            addMissingDays(habit);

                            habitList.add(habit);
                        }
                        habitAdapter.notifyDataSetChanged();
                        updateProgress();

                        if (habitList.isEmpty()) {
                            progressBar.setVisibility(View.VISIBLE);
                            progressBar.setProgress(0);
                        } else {
                            progressBar.setVisibility(View.VISIBLE);
                            updateProgress();
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(HomeActivity.this, "Failed to load habits", Toast.LENGTH_SHORT).show());
        }
    }

    // Helper function to add missing days in dailyCompletions
    private void addMissingDays(Habit habit) {
        Map<String, Boolean> dailyCompletions = habit.getDailyCompletions();
        LocalDate today = LocalDate.now();

        // Get the latest date recorded or start from a default if empty
        LocalDate lastRecordedDate = dailyCompletions.keySet().stream()
                .map(dateStr -> LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .max(LocalDate::compareTo)
                .orElse(today.minusDays(1));

        // Fill in missing days from the last recorded date to today
        for (LocalDate date = lastRecordedDate.plusDays(1); date.isBefore(today); date = date.plusDays(1)) {
            dailyCompletions.put(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), false);
        }

        // Update Firestore db with the missing days
        Map<String, Object> updates = new HashMap<>();
        for (LocalDate date = lastRecordedDate.plusDays(1); date.isBefore(today); date = date.plusDays(1)) {
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            updates.put("dailyCompletions." + dateStr, false);
        }

        if (!updates.isEmpty()) {
            db.collection("users").document(habit.getUserId()).collection("habits").document(habit.getId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> Log.d("HomeActivity", "Missing days added successfully"))
                    .addOnFailureListener(e -> Log.e("HomeActivity", "Error adding missing days", e));
        }
    }

    // Show dialog to add a new habit
    private void showAddHabitDialog() {
        if (habitList.size() >= 9) {
            Toast.makeText(this, "You cannot add more than 9 habits.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.add_habit, null);
        builder.setView(dialogView);

        EditText habitNameInput = dialogView.findViewById(R.id.habitNameInput);
        EditText habitDescriptionInput = dialogView.findViewById(R.id.habitDescriptionInput);

        builder.setTitle("Add New Habit")
                .setPositiveButton("Add", (dialog, which) -> {
                    String habitName = habitNameInput.getText().toString().trim();
                    String habitDescription = habitDescriptionInput.getText().toString().trim();

                    // Ensure habitName is not empty
                    if (!habitName.isEmpty()) {
                        addHabit(habitName, habitDescription);
                    } else {
                        Toast.makeText(HomeActivity.this, "Please enter a habit name",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }


    // Add a new habit to the list
    private void addHabit(String name, String description) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            Map<String, Object> newHabit = new HashMap<>();
            newHabit.put("title", name);
            newHabit.put("description", description);
            newHabit.put("dailyCompletions", new HashMap<String, Boolean>());
            newHabit.put("userId", userId);

            db.collection("users").document(userId).collection("habits")
                    .add(newHabit)
                    .addOnSuccessListener(documentReference -> {
                        Habit newHabitEntry = new Habit(documentReference.getId(), name, description,
                                userId);
                        newHabitEntry.setDailyCompletions(new HashMap<>());
                        habitList.add(newHabitEntry);
                        habitAdapter.notifyItemInserted(habitList.size() - 1);
                        updateProgress();
                    })
                    .addOnFailureListener(e -> Toast.makeText(HomeActivity.this,
                            "Failed to add habit", Toast.LENGTH_SHORT).show());
        }
    }

    // Edit an existing habit
    private void EditHabitDialog(int position) {
        Habit habit = habitList.get(position);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.add_habit, null);
        builder.setView(dialogView);

        EditText habitNameInput = dialogView.findViewById(R.id.habitNameInput);
        EditText habitDescriptionInput = dialogView.findViewById(R.id.habitDescriptionInput);

        // Populate fields with current habit data
        habitNameInput.setText(habit.getTitle());
        habitDescriptionInput.setText(habit.getDescription());

        builder.setTitle("Edit Habit")
                .setPositiveButton("Save", (dialog, which) -> {
                    String newTitle = habitNameInput.getText().toString().trim();
                    String newDescription = habitDescriptionInput.getText().toString().trim();

                    if (!newTitle.isEmpty() && !newDescription.isEmpty()) {
                        updateHabitInFirestore(habit, newTitle, newDescription);
                        habit.setTitle(newTitle);
                        habit.setDescription(newDescription);
                        habitAdapter.notifyItemChanged(position);
                    } else {
                        Toast.makeText(HomeActivity.this, "Please enter both name and description", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Update the habit details in Firestore
    private void updateHabitInFirestore(Habit habit, String newTitle, String newDescription) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", newTitle);
        updates.put("description", newDescription);

        db.collection("users").document(habit.getUserId())
                .collection("habits").document(habit.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d("HomeActivity", "Habit updated successfully"))
                .addOnFailureListener(e -> Toast.makeText(HomeActivity.this,
                        "Failed to update habit", Toast.LENGTH_SHORT).show());
    }

    // Show dialog to delete habits
    private void showDeleteHabitDialog() {
        String[] habitTitles = habitList.stream().map(Habit::getTitle).toArray(String[]::new);
        boolean[] selectedItems = new boolean[habitList.size()];

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Habit(s)")
                .setMultiChoiceItems(habitTitles, selectedItems,
                        (dialog, index, isChecked) -> selectedItems[index] = isChecked)
                .setPositiveButton("Delete", (dialog, which) -> deleteSelectedHabits(selectedItems))
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Delete selected habits
    private void deleteSelectedHabits(boolean[] selectedItems) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            ArrayList<Integer> itemsToRemove = new ArrayList<>();

            for (int i = 0; i < selectedItems.length; i++) {
                if (selectedItems[i]) {
                    itemsToRemove.add(i);
                }
            }

            for (int i = itemsToRemove.size() - 1; i >= 0; i--) {
                int index = itemsToRemove.get(i);
                String habitId = habitList.get(index).getId();

                db.collection("users").document(userId).collection("habits")
                        .document(habitId).delete()
                        .addOnSuccessListener(aVoid -> {
                            habitList.remove(index);
                            habitAdapter.notifyItemRemoved(index);
                            updateProgress();
                        })
                        .addOnFailureListener(e -> Toast.makeText(HomeActivity.this,
                                "Failed to delete habit", Toast.LENGTH_SHORT).show());
            }
        }
    }

    // Schedule daily habit reset
    private void scheduleDailyReset() {
        Calendar current = Calendar.getInstance();
        Calendar nextMidnight = Calendar.getInstance();
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);

        if (current.after(nextMidnight)) {
            nextMidnight.add(Calendar.DAY_OF_MONTH, 1);
        }

        long initialDelay = nextMidnight.getTimeInMillis() - System.currentTimeMillis();

        PeriodicWorkRequest dailyResetRequest = new PeriodicWorkRequest.Builder(HabitCounter.class, 1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "HabitCounter",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                dailyResetRequest
        );
    }

    // calculate and update the progress bar based on completed habits
    private void updateProgress() {
        long completedHabits = habitList.stream().filter(Habit::isCompletedToday).count();
        int totalHabits = habitList.size();
        if (totalHabits == 0) {
            progressBar.setProgress(0);
            return;
        }
        int progress = (int) ((completedHabits / (float) totalHabits) * 100);
        progressBar.setProgress(progress);
    }

    // triggers progress update based on habit's completion status
    @Override
    public void onHabitCompletionChanged() {
        updateProgress();
    }

    @Override
    public void onHabitEdit(int position) {
        EditHabitDialog(position);
    }
}
