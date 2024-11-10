package com.trackhabit.myapplication.app;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class Habit {
    private String id;
    private String title;
    private String description;
    private String userId;
    private Map<String, Boolean> dailyCompletions = new HashMap<>();

    // Empty constructor required for Firebase
    public Habit() {}

    // Constructor with parameters
    public Habit(String id, String title, String description, String userId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.userId = userId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<String, Boolean> getDailyCompletions() {
        return dailyCompletions;
    }

    public void setDailyCompletions(Map<String, Boolean> dailyCompletions) {
        this.dailyCompletions = dailyCompletions != null ? dailyCompletions : new HashMap<>();
    }

    // check if the habit is completed for today
    public boolean isCompletedToday() {
        String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return dailyCompletions.getOrDefault(todayDate, false);
    }
}