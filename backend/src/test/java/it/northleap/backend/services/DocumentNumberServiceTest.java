package it.northleap.backend.services;

import it.northleap.backend.entities.DocumentSequence;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.repositories.DocumentSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.time.Year;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class DocumentNumberServiceTest {
    @Mock private DocumentSequenceRepository documentSequenceRepository;
    @InjectMocks private DocumentNumberService documentNumberService;

    private ObjectType type(String prefix, boolean useYear, int padding, String sep) {
        ObjectType t = new ObjectType();
        t.setIcon("file-text");
        t.setKey("quote");
        t.setNumberingField("number");
        t.setNumberingPrefix(prefix);
        t.setNumberingUseYear(useYear);
        t.setNumberingPadding(padding);
        t.setNumberingSeparator(sep);
        return t;
    }

    private void counterAt(long value) {
        DocumentSequence seq = new DocumentSequence();
        seq.setCounter(value);
        when(documentSequenceRepository.findByObjectKeyAndYear(anyString(), anyInt())).thenReturn(Optional.of(seq));
    }

    @Test
    void anno_senza_prefisso_senza_padding() {
        counterAt(46);
        assertThat(documentNumberService.next(type("", true, 0, "-")))
                .isEqualTo(Year.now().getValue() + "-47");
    }

    @Test
    void prefisso_senza_anno() {
        counterAt(642);
        assertThat(documentNumberService.next(type("TT", false, 0, ""))).isEqualTo("TT643");
    }

    @Test
    void prefisso_anno_e_padding_a_quattro() {
        counterAt(0);
        assertThat(documentNumberService.next(type("FT", true, 4, "-")))
                .isEqualTo("FT" + Year.now().getValue() + "-0001");
    }

    @Test
    void il_contatore_viene_incrementato_e_salvato() {
        DocumentSequence seq = new DocumentSequence();
        seq.setCounter(9);
        when(documentSequenceRepository.findByObjectKeyAndYear(anyString(), anyInt())).thenReturn(Optional.of(seq));
        documentNumberService.next(type("", true, 0, "-"));
        assertThat(seq.getCounter()).isEqualTo(10);
    }
}
