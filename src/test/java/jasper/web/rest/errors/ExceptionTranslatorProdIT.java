package jasper.web.rest.errors;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jasper.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


/**
 * Integration tests {@link ExceptionTranslator} controller advice in production profile.
 */
@WithMockUser
@AutoConfigureMockMvc
@IntegrationTest
@ActiveProfiles({"prod", "test"})
class ExceptionTranslatorProdIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testHttpMessageConversionExceptionInProd() throws Exception {
        // In prod profile, error message should be sanitized
        mockMvc
            .perform(get("/api/exception-translator-test/http-message-conversion"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.500"))
            .andExpect(jsonPath("$.detail").value("Unable to convert http message"));
    }

    @Test
    void testDataAccessExceptionInProd() throws Exception {
        // In prod profile, error message should be sanitized
        mockMvc
            .perform(get("/api/exception-translator-test/data-access"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.500"))
            .andExpect(jsonPath("$.detail").value("Failure during data access"));
    }

    @Test
    void testInternalServerErrorWithPackageNameInProd() throws Exception {
        // In prod profile, messages with package names should be sanitized
        mockMvc
            .perform(get("/api/exception-translator-test/internal-server-error-with-package"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.500"))
            .andExpect(jsonPath("$.detail").value("Unexpected runtime exception"));
    }
}
