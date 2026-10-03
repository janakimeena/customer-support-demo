package com.example.support.ingest.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.ingest.RawCustomer;
import com.example.support.ingest.SourceUnavailableException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class CsvCustomerSourceTest {

    @Test
    void readsByHeaderNameInAnyOrderAndCaseWithLineNumbers() {
        List<RawCustomer> rows = read(csv("""
                Tier,EMAIL,Name,Region
                PREMIUM,asha@example.com,Asha Rao,east
                ,ben@example.com,"Carter, Ben",west
                """));

        assertThat(rows).containsExactly(
                new RawCustomer("test.csv:2", "Asha Rao", "asha@example.com", "PREMIUM"),
                new RawCustomer("test.csv:3", "Carter, Ben", "ben@example.com", ""));
    }

    @Test
    void tierColumnIsOptionalAndShortRowsGiveNulls() {
        List<RawCustomer> rows = read(csv("""
                name,email
                Asha Rao,asha@example.com
                Ben Carter
                """));

        assertThat(rows).extracting(RawCustomer::email, RawCustomer::tier)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("asha@example.com", null),
                        org.assertj.core.groups.Tuple.tuple(null, null));
    }

    @Test
    void missingRequiredColumnFailsTheSource() {
        assertThatThrownBy(() -> read(csv("name,mail\nAsha,asha@example.com\n")))
                .isInstanceOf(SourceUnavailableException.class)
                .hasMessageContaining("missing required columns [email]");
    }

    @Test
    void missingFileFailsTheSource() {
        assertThatThrownBy(() -> read(new ClassPathResource("no-such-file.csv")))
                .isInstanceOf(SourceUnavailableException.class)
                .hasMessageContaining("no-such-file.csv");
    }

    @Test
    void bundledSampleFileParses() {
        assertThat(read(new ClassPathResource("sample-data/customers-east.csv"))).hasSize(8);
    }

    private static List<RawCustomer> read(Resource resource) {
        try (Stream<RawCustomer> rows = new CsvCustomerSource(resource).read()) {
            return rows.toList();
        }
    }

    private static Resource csv(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "test.csv";
            }
        };
    }
}
