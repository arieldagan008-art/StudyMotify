package com.example.myapplication2;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TasksDao {

    @Insert
    void insertTask(Tasks task);

    @Query("SELECT * FROM tasks")
    List<Tasks> getAllTasks();
}
