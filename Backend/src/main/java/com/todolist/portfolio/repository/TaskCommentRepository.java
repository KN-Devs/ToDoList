package com.todolist.portfolio.repository;

import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Integer> {

    List<TaskComment> findByTaskOrderByCreatedAtAsc(Task task);
}
