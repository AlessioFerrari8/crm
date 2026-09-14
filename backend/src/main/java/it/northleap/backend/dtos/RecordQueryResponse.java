package it.northleap.backend.dtos;

import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;

import java.util.List;
import java.util.Map;

public record RecordQueryResponse(
        List<Record> items,
        long total,
        int page,
        int pageSize,
        ObjectType object,
        Map<String, String> relationTitles
) {
}
