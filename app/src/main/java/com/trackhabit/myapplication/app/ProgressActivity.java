package com.trackhabit.myapplication.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

public class ProgressActivity extends AppCompatActivity {

    private Spinner habitSpinner;
    private TextView completionStatus, totalDaysView, creationDateView, quoteTextView;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private Map<String, Boolean> dailyCompletions;
    private ArrayList<Habit> habits = new ArrayList<>();
    private String selectedHabitId;

    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        // UI Elements Initialization
        habitSpinner = findViewById(R.id.habitSpinner);
        completionStatus = findViewById(R.id.completionStatus);
        totalDaysView = findViewById(R.id.totalDaysView);
        creationDateView = findViewById(R.id.creationDateView);
        quoteTextView = findViewById(R.id.quoteTextView);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        client = new OkHttpClient();

        loadHabits();
        fetchQuote();

        // Handle spinner selection
        habitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Habit selectedHabit = habits.get(position);
                selectedHabitId = selectedHabit.getId();
                loadHabitProgress(selectedHabitId);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }

        });

        // Navigation buttons
        findViewById(R.id.homeButton).setOnClickListener(v -> startActivity(new Intent(this, HomeActivity.class)));
        findViewById(R.id.progressButton).setOnClickListener(v -> Toast.makeText(this, "Already on progress page", Toast.LENGTH_SHORT).show());
        findViewById(R.id.profileButton).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    // Load habits from Firestore
    private void loadHabits() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("habits")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    habits.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Habit habit = doc.toObject(Habit.class);
                        habit.setId(doc.getId());
                        habits.add(habit);
                    }
                    populateHabitSpinner();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load habits", Toast.LENGTH_SHORT).show());
    }

    // Populate the spinner with habit titles
    private void populateHabitSpinner() {
        ArrayList<String> habitTitles = new ArrayList<>();
        for (Habit habit : habits) {
            habitTitles.add(habit.getTitle());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, habitTitles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        habitSpinner.setAdapter(adapter);
    }

    // Load habit progress
    private void loadHabitProgress(String habitId) {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("habits")
                .document(habitId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        dailyCompletions = (Map<String, Boolean>) documentSnapshot.get("dailyCompletions");

                        if (dailyCompletions != null) {
                            int completedDays = 0;
                            int missedDays = 0;
                            LocalDate earliestDate = null;

                            for (Map.Entry<String, Boolean> entry : dailyCompletions.entrySet()) {
                                LocalDate currentDate = LocalDate.parse(entry.getKey());

                                if (earliestDate == null || currentDate.isBefore(earliestDate)) {
                                    earliestDate = currentDate;
                                }

                                if (entry.getValue()) {
                                    completedDays++;
                                } else {
                                    missedDays++;
                                }
                            }

                            int totalDays = completedDays + missedDays;
                            int completionPercentage = (int) ((completedDays / (float) totalDays) * 100);

                            completionStatus.setText("Consistency: " + completionPercentage + "%");
                            totalDaysView.setText("Completed Days: " + completedDays + " | Missed Days: " + missedDays);
                            setColorBasedOnCompletion(completionPercentage);

                            if (earliestDate != null) {
                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                                creationDateView.setText("Started on: " + earliestDate.format(formatter));
                            } else {
                                creationDateView.setText("Start date unavailable");
                            }

                            Log.d("ProgressActivity", "Habit Completion Loaded");

                            // Call to calculate consistency for each day of the week
                            calculateDayBasedInsights();
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error loading habit progress",
                        Toast.LENGTH_SHORT).show());
    }

    // Calculate consistency for each day of the week
    private void calculateDayBasedInsights() {
        // Initialize counters for each day of the week
        Map<String, int[]> dayStats = new HashMap<>();
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String day : days) {
            // {totalDays, completedDays}
            dayStats.put(day, new int[]{0, 0});
        }

        // Loop through dailyCompletions to populate counters
        if (dailyCompletions != null) {
            for (Map.Entry<String, Boolean> entry : dailyCompletions.entrySet()) {
                LocalDate date = LocalDate.parse(entry.getKey());
                String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

                // Update total count for that day
                int[] counts = dayStats.get(dayOfWeek);
                counts[0]++;
                // increment
                if (entry.getValue()) {
                    counts[1]++;
                }
            }
        }

        // Display data for each day
        setDayInsightText(R.id.monInsight, "Mon", dayStats.get("Mon"));
        setDayInsightText(R.id.tueInsight, "Tue", dayStats.get("Tue"));
        setDayInsightText(R.id.wedInsight, "Wed", dayStats.get("Wed"));
        setDayInsightText(R.id.thuInsight, "Thu", dayStats.get("Thu"));
        setDayInsightText(R.id.friInsight, "Fri", dayStats.get("Fri"));
        setDayInsightText(R.id.satInsight, "Sat", dayStats.get("Sat"));
        setDayInsightText(R.id.sunInsight, "Sun", dayStats.get("Sun"));
    }

    // Set text for each day
    private void setDayInsightText(int textViewId, String day, int[] counts) {
        TextView dayTextView = findViewById(textViewId);
        int totalDays = counts[0];
        int completedDays = counts[1];
        int consistency = (totalDays > 0) ? (completedDays * 100 / totalDays) : 0;
        dayTextView.setText(day + " " + consistency + "% ");
    }

    // Set color of score based on percentage
    private void setColorBasedOnCompletion(int completionPercentage) {
        String message;

        if (completionPercentage < 20) {
            completionStatus.setTextColor(Color.RED);
            message = "Needs Improvement!";
        } else if (completionPercentage < 40) {
            completionStatus.setTextColor(Color.parseColor("#FF4500"));
            message = "Keep Going!";
        } else if (completionPercentage < 60) {
            completionStatus.setTextColor(Color.parseColor("#FFA500"));
            message = "Good Job!";
        } else if (completionPercentage < 80) {
            completionStatus.setTextColor(Color.parseColor("#FFD700"));
            message = "Great Work!";
        } else {
            completionStatus.setTextColor(Color.GREEN);
            message = "Excellent!";
        }

        completionStatus.setText("Consistency: " + completionPercentage + "% - " + message);
    }

    // Fetch quotes functionality
    private void fetchQuote() {
        // API for motivational quotes
        Request request = new Request.Builder()
                .url("https://quoteslate.vercel.app/api/quotes/random?tags=motivation&maxLength=150")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> quoteTextView.setText("Failed to fetch quote"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseData = response.body().string();
                    try {
                        // Parse JSON response to extract quote and author
                        JSONObject jsonObject = new JSONObject(responseData);
                        String quote = jsonObject.getString("quote");
                        String author = jsonObject.getString("author");

                        // Format the quote to include the author
                        String formattedQuote = "\"" + quote + "\" - " + author;

                        // Update the TextView with the formatted quote
                        runOnUiThread(() -> quoteTextView.setText(formattedQuote));
                    } catch (Exception e) {
                        runOnUiThread(() -> quoteTextView.setText("Error parsing quote"));
                    }
                } else {
                    runOnUiThread(() -> quoteTextView.setText("No quote available"));
                }
            }
        });
    }
}
