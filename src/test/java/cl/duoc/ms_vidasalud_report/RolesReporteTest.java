package cl.duoc.ms_vidasalud_report;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La reportería es solo para Admin, en los dos endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RolesReporteTest {

    @Autowired
    MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/report/kpis", "/api/v1/report/top-services"})
    void sinTokenResponde401(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/report/kpis", "/api/v1/report/top-services"})
    void operadorNoVeLaReporteria(String url) throws Exception {
        mockMvc.perform(get(url).with(rol("Operador")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/report/kpis", "/api/v1/report/top-services"})
    void clienteNoVeLaReporteria(String url) throws Exception {
        mockMvc.perform(get(url).with(rol("Cliente")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/report/kpis", "/api/v1/report/top-services"})
    void adminVeLaReporteria(String url) throws Exception {
        mockMvc.perform(get(url).with(rol("Admin")))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor rol(String rol) {
        return jwt().authorities(new SimpleGrantedAuthority("APPROLE_" + rol));
    }
}
