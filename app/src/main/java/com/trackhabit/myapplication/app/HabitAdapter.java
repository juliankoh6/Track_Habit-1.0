package com.trackhabit.myapplication.app;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.HabitViewHolder> {

    private ArrayList<Habit> habitList;
    private FirebaseFirestore db;
    private OnHabitCompleteListener habitCompleteListener;
    private OnHabitEditListener habitEditListener;

    // Interface to notify changes in habit completion
    public interface OnHabitCompleteListener {
        void onHabitCompletionChanged();
    }

    // Interface for edit action
    public interface OnHabitEditListener {
        void onHabitEdit(int position);
    }

    // Constructor
    public HabitAdapter(ArrayList<Habit> habitList, OnHabitCompleteListener listener,
                        OnHabitEditListener editListener) {
        this.habitList = habitList;
        this.db = FirebaseFirestore.getInstance();
        this.habitCompleteListener = listener;
        this.habitEditListener = editListener;
    }

    @NonNull
    @Override
    public HabitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_habit, parent, false);
        return new HabitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HabitViewHolder holder, int position) {
        Habit habit = habitList.get(position);

        // Set the habit title and description
        holder.habitTitle.setText(habit.getTitle());
        holder.habitDescription.setText(habit.getDescription());

        // Set checkbox without triggering the listener loop
        holder.habitCheckBox.setOnCheckedChangeListener(null);
        holder.habitCheckBox.setChecked(habit.isCompletedToday());

        // Attach listener to check state
        holder.habitCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Update today's date in dailyCompletions with the current state
            String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            habit.getDailyCompletions().put(todayDate, isChecked);

            // Update Firestore to persist the change
            Map<String, Object> updates = new HashMap<>();
            updates.put("dailyCompletions." + todayDate, isChecked);

            db.collection("users")
                    .document(habit.getUserId())
                    .collection("habits")
                    .document(habit.getId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Notify listener to reflect progress
                        habitCompleteListener.onHabitCompletionChanged();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("HabitAdapter", "Error updating Firestore", e);
                        Toast.makeText(buttonView.getContext(), "Error updating habit.", Toast.
                                LENGTH_SHORT).show();
                        // Revert the checkbox state if update fails
                        buttonView.setChecked(!isChecked);
                    });
        });

        // Set click listener for edit button
        holder.editButton.setOnClickListener(v -> habitEditListener.onHabitEdit(position));
    }

    @Override
    public int getItemCount() {
        return habitList.size();
    }

    // Helper method to update progress
    public static class HabitViewHolder extends RecyclerView.ViewHolder {
        TextView habitTitle;
        TextView habitDescription;
        CheckBox habitCheckBox;
        ImageButton editButton;

        // Viewholder constructor
        public HabitViewHolder(@NonNull View itemView) {
            super(itemView);
            habitTitle = itemView.findViewById(R.id.habitTitle);
            habitDescription = itemView.findViewById(R.id.habitDescription);
            habitCheckBox = itemView.findViewById(R.id.habitCheckBox);
            editButton = itemView.findViewById(R.id.editButton);
        }
    }
}
