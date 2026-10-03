package com.example.support.ingest.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/** The HTTP side of the CRM client, against a mock server: URL, query parameters and JSON mapping. */
class CrmClientTest {

    private MockRestServiceServer server;
    private CrmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://crm.test/api");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CrmClient(builder.build());
    }

    @Test
    void fetchesOnePageOfContacts() {
        server.expect(requestTo("http://crm.test/api/contacts?page=1&size=3"))
                .andRespond(withSuccess("""
                        {"contacts":[{"contactId":7004,"fullName":"Nair, Maya","emailAddress":"maya@example.com","plan":"FREE"}],
                         "page":1,"last":true}
                        """, MediaType.APPLICATION_JSON));

        CrmPage page = client.fetchContacts(1, 3);

        assertThat(page.contacts()).containsExactly(new CrmContact(7004, "Nair, Maya", "maya@example.com", "FREE"));
        assertThat(page.last()).isTrue();
        server.verify();
    }

    @Test
    void serverErrorsSurfaceAsExceptions() {
        server.expect(requestTo("http://crm.test/api/contacts?page=0&size=3")).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchContacts(0, 3)).isInstanceOf(HttpServerErrorException.class);
    }
}
