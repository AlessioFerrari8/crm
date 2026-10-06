package it.northleap.backend.dtos;

public record CampaignStats(
        long pending,
        long sent,
        long failed,
        long skipped,
        long opened,
        long clicked) {
}
