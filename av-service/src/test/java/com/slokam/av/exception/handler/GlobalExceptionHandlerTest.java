package com.slokam.av.exception.handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.av.entity.LayoutSeat;
import com.slokam.av.dto.MovieRequest;
import com.slokam.av.entity.ReleaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void unexpectedFailuresHaveGenericResponse() {
        var response = handler.unexpected(new IllegalStateException("secret credential"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString()).doesNotContain("secret credential");
    }

    @Test
    void frameworkStatusIsPreserved() {
        var response = handler.unexpected(new ResponseStatusException(HttpStatus.NOT_FOUND, "private detail"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().toString()).doesNotContain("private detail");
    }

    @Test
    void accessDenialRemainsForbidden() {
        assertThat(handler.forbidden(request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unsupportedSeatFieldReportsItsNameAndArrayPosition() throws Exception {
        mockMvc().perform(put("/test/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"label":"A1"},{"label":"A2","editorId":"private-value"}]
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unsupported field 'editorId' in request body"))
                .andExpect(jsonPath("$.path").value("/test/seats"))
                .andExpect(jsonPath("$.fieldErrors['[1].editorId']").value("Unsupported field"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-value"))));
    }

    @Test
    void malformedJsonReportsSyntaxLocationWithoutEchoingPayload() throws Exception {
        mockMvc().perform(put("/test/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"label\":\"private-value\",]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Malformed JSON syntax")))
                .andExpect(jsonPath("$.fieldErrors.body").value(org.hamcrest.Matchers.containsString("line 1, column")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-value"))));
    }

    @Test
    void supportedSeatFieldsAreAccepted() throws Exception {
        mockMvc().perform(put("/test/seats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"label":"A1","rowNumber":1,"columnNumber":1,
                                  "category":"PREMIUM","disabled":false,"color":"#d89556"}]
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void arrayInsteadOfCountryStringIdentifiesFieldAndExpectedType() throws Exception {
        mockMvc().perform(put("/test/movies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Antahkarana\",\"countryOfOrigin\":[\"India\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("countryOfOrigin")))
                .andExpect(jsonPath("$.fieldErrors.countryOfOrigin").value("Invalid JSON type. Expected a string. Received an array."));
    }

    @Test
    void companyObjectIdentifiesArrayElement() throws Exception {
        mockMvc().perform(put("/test/movies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productionCompanies\":[\"Studio\",{\"name\":\"Antahkarana Pictures\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['productionCompanies[1]']").value("Invalid JSON type. Expected a string. Received an object."));
    }

    @Test
    void invalidProductionStatusListsEnumValues() throws Exception {
        mockMvc().perform(put("/test/movies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productionStatus\":\"In post-production\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productionStatus").value(org.hamcrest.Matchers.containsString(
                        "Expected one of: ANNOUNCED, PRE_PRODUCTION, FILMING, POST_PRODUCTION, COMPLETED.")));
    }

    @Test
    void invalidDateReportsFormat() throws Exception {
        mockMvc().perform(put("/test/movies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseDate\":\"2026-02-30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.releaseDate").value(org.hamcrest.Matchers.containsString("Expected a valid date in YYYY-MM-DD format.")));
    }

    @Test
    void invalidNumberDoesNotExposeRejectedContent() throws Exception {
        mockMvc().perform(put("/test/movies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duration\":\"private-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.duration").value(org.hamcrest.Matchers.containsString("Expected a whole number")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-value"))));
    }

    @Test
    void queryEnumReportsAllowedValuesAndMissingParameterReportsName() throws Exception {
        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/test/movies")
                        .param("releaseStatus", "Release date to be announced"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.releaseStatus").value("Invalid value. Expected one of: UPCOMING, RELEASED, DELAYED, CANCELLED."));
        mockMvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/test/movies"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.releaseStatus").value("Required parameter is missing"));
    }

    @Test
    void correctedMovieTypesAreAccepted() throws Exception {
        mockMvc().perform(put("/test/movies").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Antahkarana","countryOfOrigin":"India",
                 "productionCompanies":["Antahkarana Pictures"],"productionStatus":"POST_PRODUCTION",
                 "releaseStatus":"UPCOMING","releaseDate":"2026-10-31"}
                """))
                .andExpect(status().isOk());
    }

    @Test
    void missingBodyIdentifiesBody() throws Exception {
        mockMvc().perform(put("/test/movies").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.body").exists());
    }

    @RestController
    static class MovieInputController {
        @PutMapping("/test/movies")
        void save(@RequestBody MovieRequest movie) {}
        @org.springframework.web.bind.annotation.GetMapping("/test/movies")
        void list(@org.springframework.web.bind.annotation.RequestParam("releaseStatus") ReleaseStatus status) {}
    }

    private MockMvc mockMvc() {
        var mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return MockMvcBuilders.standaloneSetup(new SeatController(), new MovieInputController())
                .setControllerAdvice(handler)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper.findAndRegisterModules()))
                .build();
    }

    @RestController
    static class SeatController {
        @PutMapping("/test/seats")
        void save(@RequestBody List<LayoutSeat> seats) {}
    }
}
