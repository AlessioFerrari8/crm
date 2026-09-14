package it.northleap.backend.dtos;

import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordLink;

import java.util.List;
import java.util.Map;

public record RecordDetailResponse(
        Record record,
        List<RecordLink> outgoing,
        List<RecordLink> incoming,
        Map<String, String> relationTitles
) {
}
