package be.vercauteren.accounting.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L'analyse sur la vraie base: la requete du depot, la chaine de securite et la
 * forme du JSON. Les regles de calcul sont couvertes par {@code AnalysisServiceTest}.
 */
class AnalysisApiTest extends IntegrationTest {

    @Test
    @DisplayName("un lecteur compare les categories; l'annee precedente vaut year - 1 par defaut")
    void viewerComparesCategories() throws Exception {
        Supplier brasserie = supplier("Brasserie du Coin", "Brasserie", null, ExpenseCategory.RESTAURANT);
        save(invoiceOf(brasserie, admin(), 2026, 1).amountIncVat(new BigDecimal("40.00")));
        save(invoiceOf(brasserie, admin(), 2025, 1).amountIncVat(new BigDecimal("30.00")));
        User viewer = newUser("victor", UserRole.VIEWER);

        mvc.perform(get("/api/analysis/categories").param("year", "2026").with(as(viewer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previousYear").value(2025))
            .andExpect(jsonPath("$.rows[0].category").value("RESTAURANT"))
            .andExpect(jsonPath("$.rows[0].current.amountIncVat").value(40.00))
            .andExpect(jsonPath("$.rows[0].previous.amountIncVat").value(30.00))
            .andExpect(jsonPath("$.total.current.count").value(1));
    }

    @Test
    @DisplayName("nouveaux et absents, puis inventaire filtre sur une categorie")
    void comparesSuppliersAndListsInventory() throws Exception {
        Supplier brasserie = supplier("Brasserie du Coin", "Brasserie", null, ExpenseCategory.RESTAURANT);
        Supplier telecom = supplier("Telecom Exemple", "Telecom", null, ExpenseCategory.TELECOM);
        save(invoiceOf(brasserie, admin(), 2026, 1).receptionDate(LocalDate.of(2026, 2, 1)));
        save(invoiceOf(telecom, admin(), 2025, 1).receptionDate(LocalDate.of(2025, 2, 1)));

        mvc.perform(get("/api/analysis/suppliers/changes")
                .param("year", "2026").param("previousYear", "2025").param("until", "2026-10-05")
                .with(as(admin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.until").value("2026-10-05"))
            .andExpect(jsonPath("$.newSuppliers[0].supplierName").value("Brasserie du Coin"))
            .andExpect(jsonPath("$.missingSuppliers[0].supplierName").value("Telecom Exemple"));

        mvc.perform(get("/api/analysis/suppliers")
                .param("year", "2026").param("category", "RESTAURANT").with(as(admin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suppliers.length()").value(1))
            .andExpect(jsonPath("$.total.amountIncVat").value(121.00));
    }

    @Test
    @DisplayName("sans session, l'analyse est refusee")
    void requiresSession() throws Exception {
        mvc.perform(get("/api/analysis/categories").param("year", "2026"))
            .andExpect(status().isUnauthorized());
    }
}
