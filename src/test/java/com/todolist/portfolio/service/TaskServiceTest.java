package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    private User bob;
    private User alice;
    private Task bobTask;

    @BeforeEach
    void setUp() {
        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
        alice = new User(2, "Martin", "Alice", "alice@test.com", "hash", Role.ADMIN);
        bobTask = new Task(10, "Tache de Bob", "description", TaskStatus.TODO, bob);
    }

    @Test
    void getAll_whenAdmin_returnsAllTasks() {
        when(taskRepository.findAll()).thenReturn(List.of(bobTask));

        List<TaskResponse> result = taskService.getAll(alice);

        assertThat(result).hasSize(1);
        verify(taskRepository).findAll();
        verify(taskRepository, never()).findByUser(any());
    }

    @Test
    void getAll_whenUser_returnsOnlyOwnTasks() {
        when(taskRepository.findByUser(bob)).thenReturn(List.of(bobTask));

        List<TaskResponse> result = taskService.getAll(bob);

        assertThat(result).hasSize(1);
        verify(taskRepository).findByUser(bob);
        verify(taskRepository, never()).findAll();
    }

    @Test
    void getById_whenOwner_returnsTask() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));

        TaskResponse result = taskService.getById(10, bob);

        assertThat(result.id()).isEqualTo(10);
        assertThat(result.email()).isEqualTo("bob@test.com");
    }

    @Test
    void getById_whenNotOwnerAndNotAdmin_throwsAccessDenied() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));

        User stranger = new User(3, "Autre", "User", "stranger@test.com", "hash", Role.USER);

        assertThrows(AccessDeniedException.class, () -> taskService.getById(10, stranger));
    }

    @Test
    void getById_whenAdminNotOwner_returnsTask() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));

        TaskResponse result = taskService.getById(10, alice);

        assertThat(result.id()).isEqualTo(10);
    }

    @Test
    void getById_whenTaskNotFound_throwsNotFound() {
        when(taskRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> taskService.getById(999, bob));
    }

    @Test
    void create_savesTaskOwnedByCurrentUser() {
        TaskRequest request = new TaskRequest("Nouvelle tache", "desc", TaskStatus.TODO);

        TaskResponse result = taskService.create(request, bob);

        assertThat(result.email()).isEqualTo("bob@test.com");
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void update_whenOwner_updatesFields() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        TaskRequest request = new TaskRequest("Modifiee", "nouvelle desc", TaskStatus.DONE);

        TaskResponse result = taskService.update(10, request, bob);

        assertThat(result.nom()).isEqualTo("Modifiee");
        assertThat(result.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void delete_whenNotOwnerAndNotAdmin_throwsAccessDenied() {
        when(taskRepository.findById(10)).thenReturn(Optional.of(bobTask));
        User stranger = new User(3, "Autre", "User", "stranger@test.com", "hash", Role.USER);

        assertThrows(AccessDeniedException.class, () -> taskService.delete(10, stranger));
        verify(taskRepository, never()).delete(any());
    }
}
