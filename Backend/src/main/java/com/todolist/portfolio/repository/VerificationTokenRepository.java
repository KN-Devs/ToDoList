package com.todolist.portfolio.repository;

import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Integer> {

    Optional<VerificationToken> findByToken(String token);

    List<VerificationToken> findByUserAndTypeAndConsumedAtIsNull(User user, TokenType type);

    Optional<VerificationToken> findByProjectAndUserAndTypeAndConsumedAtIsNull(
            Project project, User user, TokenType type);

    List<VerificationToken> findByProjectAndTypeAndConsumedAtIsNull(Project project, TokenType type);
}
