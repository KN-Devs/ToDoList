package com.todolist.portfolio.entity;

import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "project_members")
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "can_manage_tasks", nullable = false)
    private boolean canManageTasks = false;

    public ProjectMember() {
    }

    public ProjectMember(Project project, User user, boolean canManageTasks) {
        this.project = project;
        this.user = user;
        this.canManageTasks = canManageTasks;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isCanManageTasks() {
        return canManageTasks;
    }

    public void setCanManageTasks(boolean canManageTasks) {
        this.canManageTasks = canManageTasks;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProjectMember that = (ProjectMember) o;
        return Objects.equals(id, that.id) && Objects.equals(project, that.project) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, project, user);
    }

    @Override
    public String toString() {
        return "ProjectMember{" +
                "id=" + id +
                ", user=" + user +
                ", canManageTasks=" + canManageTasks +
                '}';
    }
}
