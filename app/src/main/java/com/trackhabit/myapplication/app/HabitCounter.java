package com.trackhabit.myapplication.app;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class HabitCounter extends Worker {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Constructor
    public HabitCounter(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
    }

    @NonNull
    @Override
    // Helper method to reset the checkboxes during start of new day
    public Result doWork() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("habits")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String habitId = doc.getId();
                        String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("dailyCompletions." + todayDate, false);

                        db.collection("users").document(userId).collection("habits").document(habitId).update(updates);
                    }
                })
                .addOnFailureListener(e -> Log.e("HabitCounter", "Failed to reset habits", e));

        return Result.success();
    }
}
