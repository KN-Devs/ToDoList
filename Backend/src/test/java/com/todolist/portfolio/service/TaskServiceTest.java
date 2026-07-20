package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.TaskStatus;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private TaskService taskService;

    private User bob;
    private User stranger;
    private Project bobProject;
    private Task bobTask;

    @BeforeEach
    void setUp() {
        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
        stranger = new User(3, "Autre", "User", "stranger@test.com", "hash", Role.USER);
        bobProject = new Project(10, "Projet de Bob", "description",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), bob);
        bobTask = new Task(10, "Tache de Bob", "description", TaskStatus.TODO, bob, bobProject);
    }

    @Test
    void getAllForProject_returnsProjectTasks() {
        when(projectService.findOrThrow(10)).thenReturn(bobProject);
        when(taskRepository.findByProject(bobProject)).thenReturn(List.of(bobTask));

        List<TaskResponse> result = taskService.getAllForProject(10, bob);

        assertThat(result).hasSize(1);
        verify(projectService).checkCanView(bobProject, bob);
    }

    @Test
    void getAllForProject_whenNoAccess_throwsAccessDenied() {
        when(projectService.findOrThrow(10)).thenReturn(bobProject);
        doThrow(new AccessDeniedException("no")).when(projectService).checkCanView(bobProject, stranger);

        assertThrows(AccessDeniedException.class, () -> taskService.getAllForProject(10, stranger));
    }

    @Test
    void getById_whenAllowed_returnsTask() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));

        TaskResponse result = taskService.getById(10, bob);

        assertThat(result.id()).isEqualTo(10);
        assertThat(result.email()).isEqualTo("bob@test.com");
    }

    @Test
    void getById_whenNoAccess_throwsAccessDenied() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        doThrow(new AccessDeniedException("no")).when(projectService).checkCanView(bobProject, stranger);

        assertThrows(AccessDeniedException.class, () -> taskService.getById(10, stranger));
    }

    @Test
    void getById_whenTaskNotFound_throwsNotFound() {
        when(taskRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> taskService.getById(999, bob));
    }

    @Test
    void create_whenAllowed_savesTaskInProject() {
        when(projectService.findOrThrow(10)).thenReturn(bobProject);
        TaskRequest request = new TaskRequest("Nouvelle tache", "desc", TaskStatus.TODO);

        TaskResponse result = taskService.create(10, request, bob);

        assertThat(result.email()).isEqualTo("bob@test.com");
        verify(projectService).checkCanManageTasks(bobProject, bob);
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void create_whenNotAllowed_throwsAccessDenied() {
        when(projectService.findOrThrow(10)).thenReturn(bobProject);
        doThrow(new AccessDeniedException("no")).when(projectService).checkCanManageTasks(bobProject, stranger);
        TaskRequest request = new TaskRequest("Nouvelle tache", "desc", TaskStatus.TODO);

        assertThrows(AccessDeniedException.class, () -> taskService.create(10, request, stranger));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void update_whenManageRights_updatesAllFields() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        when(projectService.hasManageRights(bobProject, bob)).thenReturn(true);
        TaskRequest request = new TaskRequest("Modifiee", "nouvelle desc", TaskStatus.DONE);

        TaskResponse result = taskService.update(10, request, bob);

        assertThat(result.nom()).isEqualTo("Modifiee");
        assertThat(result.description()).isEqualTo("nouvelle desc");
        assertThat(result.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void create_withDueDate_savesIt() {
        when(projectService.findOrThrow(10)).thenReturn(bobProject);
        LocalDate dueDate = LocalDate.of(2026, 3, 15);
        TaskRequest request = new TaskRequest("Nouvelle tache", "desc", TaskStatus.TODO, dueDate);

        TaskResponse result = taskService.create(10, request, bob);

        assertThat(result.dueDate()).isEqualTo(dueDate);
    }

    @Test
    void update_whenManageRights_updatesDueDate() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        when(projectService.hasManageRights(bobProject, bob)).thenReturn(true);
        LocalDate dueDate = LocalDate.of(2026, 3, 15);
        TaskRequest request = new TaskRequest(bobTask.getNom(), bobTask.getDescription(), TaskStatus.TODO, dueDate);

        TaskResponse result = taskService.update(10, request, bob);

        assertThat(result.dueDate()).isEqualTo(dueDate);
    }

    @Test
    void update_whenViewOnlyMemberTriesToChangeDueDate_throwsAccessDenied() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        when(projectService.hasManageRights(bobProject, stranger)).thenReturn(false);
        TaskRequest request = new TaskRequest(bobTask.getNom(), bobTask.getDescription(), TaskStatus.DONE,
                LocalDate.of(2026, 3, 15));

        assertThrows(AccessDeniedException.class, () -> taskService.update(10, request, stranger));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void update_whenViewOnlyMember_canChangeStatusOnly() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        when(projectService.hasManageRights(bobProject, stranger)).thenReturn(false);
        TaskRequest request = new TaskRequest(bobTask.getNom(), bobTask.getDescription(), TaskStatus.DONE);

        TaskResponse result = taskService.update(10, request, stranger);

        assertThat(result.status()).isEqualTo(TaskStatus.DONE);
        verify(projectService).checkCanView(bobProject, stranger);
    }

    @Test
    void update_whenViewOnlyMemberTriesToRenameTask_throwsAccessDenied() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        when(projectService.hasManageRights(bobProject, stranger)).thenReturn(false);
        TaskRequest request = new TaskRequest("Renommee", bobTask.getDescription(), TaskStatus.DONE);

        assertThrows(AccessDeniedException.class, () -> taskService.update(10, request, stranger));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void update_whenNoProjectAccess_throwsAccessDenied() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        when(projectService.hasManageRights(bobProject, stranger)).thenReturn(false);
        doThrow(new AccessDeniedException("no")).when(projectService).checkCanView(bobProject, stranger);
        TaskRequest request = new TaskRequest(bobTask.getNom(), bobTask.getDescription(), TaskStatus.DONE);

        assertThrows(AccessDeniedException.class, () -> taskService.update(10, request, stranger));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void delete_whenNotAllowed_throwsAccessDenied() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        doThrow(new AccessDeniedException("no")).when(projectService).checkCanManageTasks(bobProject, stranger);

        assertThrows(AccessDeniedException.class, () -> taskService.delete(10, stranger));
        verify(taskRepository, never()).delete(any());
    }
}
