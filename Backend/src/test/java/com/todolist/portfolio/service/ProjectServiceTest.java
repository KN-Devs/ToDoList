package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.ProjectRepository;
import com.todolist.portfolio.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    private User bob;
    private User alice;
    private User carol;
    private Project bobProject;

    @BeforeEach
    void setUp() {
        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
        alice = new User(2, "Martin", "Alice", "alice@test.com", "hash", Role.ADMIN);
        carol = new User(3, "Petit", "Carol", "carol@test.com", "hash", Role.USER);
        bobProject = new Project(10, "Projet de Bob", "description",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), bob);
    }

    @Test
    void getAll_whenAdmin_returnsAllProjects() {
        when(projectRepository.findAll()).thenReturn(List.of(bobProject));

        List<ProjectResponse> result = projectService.getAll(alice);

        assertThat(result).hasSize(1);
        verify(projectRepository).findAll();
        verify(projectRepository, never()).findAllForUser(any());
    }

    @Test
    void getAll_whenUser_returnsOwnedAndMemberProjects() {
        when(projectRepository.findAllForUser(bob)).thenReturn(List.of(bobProject));

        List<ProjectResponse> result = projectService.getAll(bob);

        assertThat(result).hasSize(1);
        verify(projectRepository).findAllForUser(bob);
    }

    @Test
    void getById_whenStranger_throwsAccessDenied() {
        when(projectRepository.findById(10)).thenReturn(Optional.of(bobProject));

        assertThrows(AccessDeniedException.class, () -> projectService.getById(10, carol));
    }

    @Test
    void getById_whenNotFound_throwsNotFound() {
        when(projectRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> projectService.getById(999, bob));
    }

    @Test
    void create_savesProjectOwnedByCurrentUser() {
        ProjectRequest request = new ProjectRequest("Nouveau projet", "desc",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        ProjectResponse result = projectService.create(request, bob);

        assertThat(result.ownerEmail()).isEqualTo("bob@test.com");
        verify(projectRepository, times(1)).save(any(Project.class));
    }

    @Test
    void create_whenEndDateBeforeStartDate_throwsBadRequest() {
        ProjectRequest request = new ProjectRequest("Nouveau projet", "desc",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1));

        assertThrows(ResponseStatusException.class, () -> projectService.create(request, bob));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void update_whenNotOwnerAndNotAdmin_throwsAccessDenied() {
        when(projectRepository.findById(10)).thenReturn(Optional.of(bobProject));
        ProjectRequest request = new ProjectRequest("Modifie", "desc",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

        assertThrows(AccessDeniedException.class, () -> projectService.update(10, request, carol));
    }

    @Test
    void addMember_whenOwner_addsExistingUser() {
        when(projectRepository.findById(10)).thenReturn(Optional.of(bobProject));
        when(userRepository.findByEmail("carol@test.com")).thenReturn(Optional.of(carol));

        ProjectResponse result = projectService.addMember(10, "carol@test.com", bob);

        assertThat(result.memberEmails()).containsExactly("carol@test.com");
    }

    @Test
    void addMember_whenNotOwner_throwsAccessDenied() {
        when(projectRepository.findById(10)).thenReturn(Optional.of(bobProject));

        assertThrows(AccessDeniedException.class, () -> projectService.addMember(10, "carol@test.com", carol));
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void addMember_whenEmailUnknown_throwsNotFound() {
        when(projectRepository.findById(10)).thenReturn(Optional.of(bobProject));
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> projectService.addMember(10, "ghost@test.com", bob));
    }

    @Test
    void addMember_whenAddingOwner_throwsBadRequest() {
        when(projectRepository.findById(10)).thenReturn(Optional.of(bobProject));
        when(userRepository.findByEmail("bob@test.com")).thenReturn(Optional.of(bob));

        assertThrows(ResponseStatusException.class, () -> projectService.addMember(10, "bob@test.com", bob));
    }

    @Test
    void removeMember_whenOwner_removesMember() {
        bobProject.getMembers().add(carol);
        when(projectRepository.findById(10)).thenReturn(Optional.of(bobProject));

        ProjectResponse result = projectService.removeMember(10, carol.getEmail(), bob);

        assertThat(result.memberEmails()).isEmpty();
    }

    @Test
    void delete_whenNotOwnerAndNotAdmin_throwsAccessDenied() {
        when(projectRepository.findById(10)).thenReturn(Optional.of(bobProject));

        assertThrows(AccessDeniedException.class, () -> projectService.delete(10, carol));
        verify(projectRepository, never()).delete(any());
    }
}
