package com.todolist.portfolio.repository;

import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Integer> {
    
    List<Task> findByUser(User user);
}
