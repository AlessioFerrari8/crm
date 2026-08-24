package it.northleap.backend.dtos;

import java.util.ArrayList;
import java.util.List;

public record ReportResult(List<List<Object>> dimensions, List<List<Number>> metrics) {
    public static ReportResult of(int dimCount, int metricCount, List<Object[]> rows) {
        List<List<Object>> dims = new ArrayList<>();
        List<List<Number>> mets = new ArrayList<>();
        for (Object[] row : rows) {
            List<Object> d = new ArrayList<>();
            for (int i = 0; i < dimCount; i++) d.add(row[i]);
            List<Number> m = new ArrayList<>();
            for (int i = 0; i < metricCount; i++) m.add((Number) row[dimCount + i]);
            dims.add(d);
            mets.add(m);
        }
        return new ReportResult(dims, mets);
    }
}