package com.todolist.portfolio.dto;

import java.time.Instant;

public record AttachmentResponse(Integer id, String filename, String contentType, long fileSize,
                                  String uploadedByEmail, Instant createdAt) {
}
