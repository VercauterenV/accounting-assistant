package be.vercauteren.accounting.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.service.AnalysisService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Lecture des parametres, sans base: l'annee precedente par defaut et la date de coupure. */
class AnalysisControllerTest {

    private AnalysisService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AnalysisService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AnalysisController(service)).build();
    }

    @Test
    @DisplayName("sans previousYear, on compare a l'annee d'avant")
    void defaultsToPreviousYear() throws Exception {
        mvc.perform(get("/api/analysis/categories").param("year", "2026"))
            .andExpect(status().isOk());
        verify(service).compareCategories(2026, 2025, null);

        mvc.perform(get("/api/analysis/suppliers/changes").param("year", "2026"))
            .andExpect(status().isOk());
        verify(service).compareSuppliers(2026, 2025, null);
    }

    @Test
    @DisplayName("previousYear et until sont transmis tels quels")
    void passesExplicitParameters() throws Exception {
        mvc.perform(get("/api/analysis/categories")
                .param("year", "2026").param("previousYear", "2024").param("until", "2026-10-05"))
            .andExpect(status().isOk());
        verify(service).compareCategories(2026, 2024, LocalDate.of(2026, 10, 5));

        mvc.perform(get("/api/analysis/suppliers/changes")
                .param("year", "2026").param("previousYear", "2024").param("until", "2026-10-05"))
            .andExpect(status().isOk());
        verify(service).compareSuppliers(2026, 2024, LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("l'inventaire prend une categorie facultative")
    void passesInventoryCategory() throws Exception {
        mvc.perform(get("/api/analysis/suppliers").param("year", "2026").param("category", "TELECOM"))
            .andExpect(status().isOk());
        verify(service).inventory(2026, ExpenseCategory.TELECOM);

        mvc.perform(get("/api/analysis/suppliers").param("year", "2025"))
            .andExpect(status().isOk());
        verify(service).inventory(eq(2025), isNull());
    }
}
