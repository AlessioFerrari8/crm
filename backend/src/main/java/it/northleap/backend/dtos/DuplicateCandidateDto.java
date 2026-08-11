package it.northleap.backend.dtos;

import java.util.UUID;

public record DuplicateCandidateDto(UUID leftId, String leftTitle, UUID rightId, String rightTitle, double score) {
}
