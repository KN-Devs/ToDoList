package com.todolist.portfolio.repository;

import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Integer> {

    List<Task> findByProject(Project project);

    // Tâches visibles par l'utilisateur (propriétaire du projet ou membre),
    // non terminées, avec une échéance dépassée ou proche (<= threshold).
    @Query("SELECT DISTINCT t FROM Task t JOIN t.project p LEFT JOIN p.members m " +
            "WHERE (p.owner = :user OR m.user = :user) " +
            "AND t.dueDate IS NOT NULL AND t.status <> com.todolist.portfolio.entity.TaskStatus.DONE " +
            "AND t.dueDate <= :threshold " +
            "ORDER BY t.dueDate ASC")
    List<Task> findDueSoonOrOverdueForUser(@Param("user") User user, @Param("threshold") LocalDate threshold);
}
