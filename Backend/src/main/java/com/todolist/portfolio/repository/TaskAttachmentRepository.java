package com.todolist.portfolio.repository;

import com.todolist.portfolio.dto.AttachmentResponse;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Integer> {

    // Ne charge jamais le contenu binaire pour un simple listage : seule la
    // consultation d'une pièce jointe précise (téléchargement) charge ses octets.
    @Query("SELECT new com.todolist.portfolio.dto.AttachmentResponse(" +
            "a.id, a.filename, a.contentType, a.fileSize, a.uploadedBy.email, a.createdAt) " +
            "FROM TaskAttachment a WHERE a.task = :task ORDER BY a.createdAt ASC")
    List<AttachmentResponse> findMetadataByTask(@Param("task") Task task);
}
