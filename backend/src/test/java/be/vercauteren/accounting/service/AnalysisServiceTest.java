package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.vercauteren.accounting.dto.CategoryComparisonResponse;
import be.vercauteren.accounting.dto.CategoryComparisonRow;
import be.vercauteren.accounting.dto.SupplierChangesResponse;
import be.vercauteren.accounting.dto.SupplierInventoryResponse;
import be.vercauteren.accounting.dto.SupplierSummary;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Les sommes de l'ecran "Analyse". Le depot est simule: il rend les achats des
 * annees demandees, et c'est le service qui coupe, regroupe et additionne.
 */
class AnalysisServiceTest {

    private static final Supplier BRASSERIE = supplier(1L, "Brasserie du Coin", ExpenseCategory.RESTAURANT);
    private static final Supplier CHEZ_LEON = supplier(2L, "chez Léon", ExpenseCategory.RESTAURANT);
    private static final Supplier TELECOM = supplier(3L, "Telecom Exemple", ExpenseCategory.TELECOM);
    private static final Supplier SANS_CATEGORIE = supplier(4L, "Divers SA", null);

    private final List<Invoice> ledger = new ArrayList<>();
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        InvoiceRepository repository = mock(InvoiceRepository.class);
        when(repository.findByTypeAndYearIn(eq(InvoiceType.PURCHASE), any())).thenAnswer(call -> {
            Collection<Integer> years = call.getArgument(1);
            return ledger.stream()
                .filter(invoice -> invoice.getType() == InvoiceType.PURCHASE)
                .filter(invoice -> years.contains(invoice.getYear()))
                .toList();
        });
        service = new AnalysisService(repository);
    }

    @Test
    @DisplayName("compare les categories d'une annee a l'autre, la plus grosse en tete, total compris")
    void comparesCategories() {
        purchase(BRASSERIE, "2026-03-10", "100.00", "82.64");
        purchase(CHEZ_LEON, "2026-04-02", "50.50", "41.74");
        purchase(BRASSERIE, "2025-03-12", "80.00", "66.12");
        purchase(TELECOM, "2025-06-01", "300.00", "247.93");
        purchase(TELECOM, "2026-06-01", "200.00", "165.29");
        purchase(SANS_CATEGORIE, "2026-01-05", "10.00", null);

        CategoryComparisonResponse response = service.compareCategories(2026, 2025, null);

        assertThat(response.rows()).extracting(CategoryComparisonRow::category)
            .containsExactly(ExpenseCategory.TELECOM, ExpenseCategory.RESTAURANT, null);
        CategoryComparisonRow restaurant = response.rows().get(1);
        assertThat(restaurant.current().amountIncVat()).isEqualByComparingTo("150.50");
        assertThat(restaurant.current().amountExVat()).isEqualByComparingTo("124.38");
        assertThat(restaurant.current().count()).isEqualTo(2);
        assertThat(restaurant.previous().amountIncVat()).isEqualByComparingTo("80.00");
        assertThat(restaurant.previous().count()).isEqualTo(1);

        CategoryComparisonRow none = response.rows().get(2);
        assertThat(none.previous().count()).isZero();
        assertThat(none.previous().amountIncVat()).isEqualByComparingTo("0");

        assertThat(response.total().category()).isNull();
        assertThat(response.total().current().amountIncVat()).isEqualByComparingTo("360.50");
        assertThat(response.total().previous().amountIncVat()).isEqualByComparingTo("380.00");
        assertThat(response.missingAmountCount()).isZero();
        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.previousYear()).isEqualTo(2025);
    }

    @Test
    @DisplayName("periode comparable: les deux annees s'arretent au meme jour du calendrier")
    void cutsBothYearsOnTheSameDay() {
        purchase(BRASSERIE, "2026-10-05", "40.00", null);
        purchase(BRASSERIE, "2026-10-06", "999.00", null);
        purchase(BRASSERIE, "2025-10-05", "30.00", null);
        purchase(BRASSERIE, "2025-12-20", "500.00", null);

        CategoryComparisonResponse response = service.compareCategories(2026, 2025, LocalDate.of(2026, 10, 5));

        assertThat(response.until()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(response.total().current().amountIncVat()).isEqualByComparingTo("40.00");
        assertThat(response.total().previous().amountIncVat()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("un montant manquant est compte comme facture, jamais somme comme zero")
    void countsMissingAmountsApart() {
        purchase(BRASSERIE, "2026-02-01", "20.00", "16.53");
        purchase(BRASSERIE, "2026-02-02", null, null);
        purchase(BRASSERIE, "2025-02-02", null, null);

        CategoryComparisonResponse response = service.compareCategories(2026, 2025, null);

        assertThat(response.missingAmountCount()).isEqualTo(2);
        assertThat(response.total().current().count()).isEqualTo(2);
        assertThat(response.total().current().amountIncVat()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("les ventes ne sont jamais des depenses")
    void ignoresSales() {
        purchase(BRASSERIE, "2026-02-01", "20.00", null);
        ledger.add(invoice(TELECOM, "2026-02-01", "5000.00", null, InvoiceType.SALE));

        CategoryComparisonResponse response = service.compareCategories(2026, 2025, null);

        assertThat(response.rows()).extracting(CategoryComparisonRow::category)
            .containsExactly(ExpenseCategory.RESTAURANT);
        assertThat(response.total().current().amountIncVat()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("nouveaux: jamais vus l'an dernier; absents: pas encore revus cette annee")
    void comparesSuppliers() {
        purchase(BRASSERIE, "2026-03-01", "10.00", null);
        purchase(BRASSERIE, "2025-03-01", "10.00", null);
        purchase(CHEZ_LEON, "2026-02-01", "25.00", "20.66");
        purchase(CHEZ_LEON, "2026-05-01", "15.00", "12.40");
        purchase(TELECOM, "2025-04-01", "60.00", null);
        // Facture chaque decembre: pas encore attendue a la date de coupure.
        purchase(SANS_CATEGORIE, "2025-12-15", "90.00", null);
        // Vu en decembre dernier seulement: pas "nouveau" pour autant.
        Supplier insurer = supplier(5L, "Assurance Exemple", ExpenseCategory.ASSURANCE);
        purchase(insurer, "2025-11-20", "300.00", null);
        purchase(insurer, "2026-01-20", "310.00", null);

        SupplierChangesResponse response = service.compareSuppliers(2026, 2025, LocalDate.of(2026, 10, 5));

        assertThat(response.newSuppliers()).singleElement().satisfies(summary -> {
            assertThat(summary.supplierName()).isEqualTo("chez Léon");
            assertThat(summary.category()).isEqualTo(ExpenseCategory.RESTAURANT);
            assertThat(summary.count()).isEqualTo(2);
            assertThat(summary.amountIncVat()).isEqualByComparingTo("40.00");
            assertThat(summary.amountExVat()).isEqualByComparingTo("33.06");
            assertThat(summary.firstDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(summary.lastDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        });
        assertThat(response.missingSuppliers()).extracting(SupplierSummary::supplierName)
            .containsExactly("Telecom Exemple");
    }

    @Test
    @DisplayName("inventaire: par fournisseur, le plus gros d'abord, a egalite par nom")
    void listsPurchasesBySupplier() {
        purchase(TELECOM, "2026-01-10", "50.00", null);
        purchase(CHEZ_LEON, "2026-01-11", "50.00", null);
        purchase(BRASSERIE, "2026-01-12", "20.00", null);
        purchase(BRASSERIE, "2026-03-12", "30.00", null);
        purchase(SANS_CATEGORIE, "2026-03-12", null, null);
        purchase(TELECOM, "2025-01-10", "999.00", null);

        SupplierInventoryResponse response = service.inventory(2026, null);

        assertThat(response.suppliers()).extracting(SupplierSummary::supplierName)
            .containsExactly("Brasserie du Coin", "chez Léon", "Telecom Exemple", "Divers SA");
        assertThat(response.total().amountIncVat()).isEqualByComparingTo("150.00");
        assertThat(response.total().count()).isEqualTo(5);
        assertThat(response.missingAmountCount()).isEqualTo(1);
        assertThat(response.category()).isNull();
    }

    @Test
    @DisplayName("inventaire filtre sur une categorie")
    void filtersInventoryOnCategory() {
        purchase(TELECOM, "2026-01-10", "50.00", null);
        purchase(CHEZ_LEON, "2026-01-11", "50.00", null);
        purchase(SANS_CATEGORIE, "2026-03-12", "5.00", null);

        SupplierInventoryResponse response = service.inventory(2026, ExpenseCategory.RESTAURANT);

        assertThat(response.suppliers()).extracting(SupplierSummary::supplierName)
            .containsExactly("chez Léon");
        assertThat(response.total().amountIncVat()).isEqualByComparingTo("50.00");
        assertThat(response.category()).isEqualTo(ExpenseCategory.RESTAURANT);
    }

    private void purchase(Supplier supplier, String date, String incVat, String exVat) {
        ledger.add(invoice(supplier, date, incVat, exVat, InvoiceType.PURCHASE));
    }

    private static Invoice invoice(Supplier supplier, String date, String incVat, String exVat, InvoiceType type) {
        LocalDate reception = LocalDate.parse(date);
        return Invoice.builder()
            .year(reception.getYear())
            .type(type)
            .supplier(supplier)
            .receptionDate(reception)
            .amountIncVat(incVat == null ? null : new BigDecimal(incVat))
            .amountExVat(exVat == null ? null : new BigDecimal(exVat))
            .build();
    }

    private static Supplier supplier(Long id, String name, ExpenseCategory category) {
        return Supplier.builder().id(id).name(name).category(category).build();
    }
}
