package com.example.support.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.support.ingest.IngestionProperties.SourceDefinition;
import com.example.support.ingest.crm.CrmCustomerSource;
import com.example.support.ingest.csv.CsvCustomerSource;
import com.example.support.ingest.legacy.LegacyAccountsSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

class CustomerSourceFactoryTest {

    private final CustomerSourceFactory factory =
            new CustomerSourceFactory(new DefaultResourceLoader(), RestClient.builder(), mock(JdbcTemplate.class));

    @Test
    void buildsTheImplementationForEachType() {
        assertThat(factory.create(new SourceDefinition(SourceType.CSV, "classpath:sample-data/customers-east.csv")))
                .isInstanceOf(CsvCustomerSource.class);
        assertThat(factory.create(new SourceDefinition(SourceType.CRM_API, "http://crm.test")))
                .isInstanceOf(CrmCustomerSource.class);
        assertThat(factory.create(new SourceDefinition(SourceType.LEGACY_DB, null)))
                .isInstanceOf(LegacyAccountsSource.class);
    }

    @Test
    void sourcesThatNeedALocationRequireOne() {
        assertThatThrownBy(() -> factory.create(new SourceDefinition(SourceType.CSV, " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV source needs a location");
    }
}
