package com.todolist.portfolio.entity;


import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.Objects;


@Entity
@Table(name = "task")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Integer id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "due_date")
    private LocalDate dueDate;

    public Task() {
    }

    public Task(Integer id, String nom, String description, TaskStatus status, User user, Project project) {
        this.id = id;
        this.nom = nom;
        this.description = description;
        this.status = status;
        this.user = user;
        this.project = project;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(id, task.id) && Objects.equals(nom, task.nom) && Objects.equals(description, task.description) && status == task.status && Objects.equals(user, task.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nom, description, status, user);
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", nom='" + nom + '\'' +
                ", description='" + description + '\'' +
                ", status=" + status +
                ", user=" + user +
                '}';
    }
}


