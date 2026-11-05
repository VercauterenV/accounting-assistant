package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.AnalysisTotal;
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
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agregats de l'ecran "Analyse". Toutes les sommes sont faites ici, en
 * {@link BigDecimal}: un futur assistant qui repondrait aux questions en
 * langage naturel appellerait ces methodes, il ne refait aucun calcul.
 *
 * <p>Regles communes:
 * <ul>
 *   <li>achats uniquement, toutes series confondues — un loyer sans document
 *       reste une depense;</li>
 *   <li>regroupement par annee du facturier ({@code year}), pas par date de
 *       reception;</li>
 *   <li>un montant manquant n'est jamais somme comme zero: la facture est
 *       comptee, et la reponse dit combien n'en avaient pas;</li>
 *   <li>periode comparable: avec {@code until}, chaque annee est coupee au meme
 *       jour du calendrier de {@code receptionDate}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

    private static final Comparator<ExpenseCategory> CATEGORY_ORDER =
        Comparator.nullsLast(Comparator.naturalOrder());

    private static final Comparator<SupplierSummary> SUPPLIER_ORDER =
        Comparator.comparing(SupplierSummary::amountIncVat, Comparator.reverseOrder())
            .thenComparing(SupplierSummary::supplierName, String.CASE_INSENSITIVE_ORDER);

    private final InvoiceRepository invoiceRepository;

    public CategoryComparisonResponse compareCategories(int year, int previousYear, LocalDate until) {
        List<Invoice> purchases = purchases(year, previousYear);
        List<Invoice> current = within(inYear(purchases, year), until);
        List<Invoice> previous = within(inYear(purchases, previousYear), until);

        Map<ExpenseCategory, List<Invoice>> currentByCategory = groupBy(current, AnalysisService::category);
        Map<ExpenseCategory, List<Invoice>> previousByCategory = groupBy(previous, AnalysisService::category);

        Set<ExpenseCategory> categories = new HashSet<>(currentByCategory.keySet());
        categories.addAll(previousByCategory.keySet());

        List<CategoryComparisonRow> rows = categories.stream()
            .map(category -> new CategoryComparisonRow(
                category,
                total(currentByCategory.getOrDefault(category, List.of())),
                total(previousByCategory.getOrDefault(category, List.of()))))
            .sorted(Comparator
                .comparing((CategoryComparisonRow row) -> row.current().amountIncVat()
                    .max(row.previous().amountIncVat()), Comparator.reverseOrder())
                .thenComparing(CategoryComparisonRow::category, CATEGORY_ORDER))
            .toList();

        CategoryComparisonRow totalRow = new CategoryComparisonRow(null, total(current), total(previous));
        int missing = missingAmountCount(current) + missingAmountCount(previous);
        return new CategoryComparisonResponse(year, previousYear, until, rows, totalRow, missing);
    }

    public SupplierChangesResponse compareSuppliers(int year, int previousYear, LocalDate until) {
        List<Invoice> purchases = purchases(year, previousYear);
        List<Invoice> currentYear = inYear(purchases, year);
        List<Invoice> previousYearAll = inYear(purchases, previousYear);

        Set<Long> seenCurrentYear = supplierIds(currentYear);
        Set<Long> seenPreviousYear = supplierIds(previousYearAll);

        List<SupplierSummary> newSuppliers = summarize(within(currentYear, until)).stream()
            .filter(summary -> !seenPreviousYear.contains(summary.supplierId()))
            .toList();
        List<SupplierSummary> missingSuppliers = summarize(within(previousYearAll, until)).stream()
            .filter(summary -> !seenCurrentYear.contains(summary.supplierId()))
            .toList();

        return new SupplierChangesResponse(year, previousYear, until, newSuppliers, missingSuppliers);
    }

    public SupplierInventoryResponse inventory(int year, ExpenseCategory category) {
        List<Invoice> invoices = purchases(year).stream()
            .filter(invoice -> category == null || category == category(invoice))
            .toList();
        return new SupplierInventoryResponse(
            year, category, summarize(invoices), total(invoices), missingAmountCount(invoices));
    }

    private List<Invoice> purchases(Integer... years) {
        return invoiceRepository.findByTypeAndYearIn(InvoiceType.PURCHASE, List.of(years));
    }

    private static List<Invoice> inYear(List<Invoice> invoices, int year) {
        return invoices.stream().filter(invoice -> invoice.getYear() == year).toList();
    }

    /** Garde les factures recues au plus tard le meme jour du calendrier que {@code until}. */
    private static List<Invoice> within(List<Invoice> invoices, LocalDate until) {
        if (until == null) {
            return invoices;
        }
        MonthDay limit = MonthDay.from(until);
        return invoices.stream()
            .filter(invoice -> !MonthDay.from(invoice.getReceptionDate()).isAfter(limit))
            .toList();
    }

    private static ExpenseCategory category(Invoice invoice) {
        return invoice.getSupplier().getCategory();
    }

    private static <K> Map<K, List<Invoice>> groupBy(List<Invoice> invoices, Function<Invoice, K> key) {
        // LinkedHashMap plutot que groupingBy: la categorie peut etre nulle.
        Map<K, List<Invoice>> groups = new LinkedHashMap<>();
        for (Invoice invoice : invoices) {
            groups.computeIfAbsent(key.apply(invoice), ignored -> new ArrayList<>()).add(invoice);
        }
        return groups;
    }

    private static Set<Long> supplierIds(List<Invoice> invoices) {
        Set<Long> ids = new HashSet<>();
        invoices.forEach(invoice -> ids.add(invoice.getSupplier().getId()));
        return ids;
    }

    private static List<SupplierSummary> summarize(List<Invoice> invoices) {
        return groupBy(invoices, invoice -> invoice.getSupplier().getId()).values().stream()
            .map(AnalysisService::summary)
            .sorted(SUPPLIER_ORDER)
            .toList();
    }

    private static SupplierSummary summary(List<Invoice> invoices) {
        Supplier supplier = invoices.getFirst().getSupplier();
        AnalysisTotal total = total(invoices);
        List<LocalDate> dates = invoices.stream().map(Invoice::getReceptionDate).sorted().toList();
        return new SupplierSummary(
            supplier.getId(),
            supplier.getName(),
            supplier.getCategory(),
            total.count(),
            total.amountIncVat(),
            total.amountExVat(),
            dates.getFirst(),
            dates.getLast());
    }

    private static AnalysisTotal total(List<Invoice> invoices) {
        return new AnalysisTotal(
            sum(invoices, Invoice::getAmountIncVat),
            sum(invoices, Invoice::getAmountExVat),
            invoices.size());
    }

    private static BigDecimal sum(List<Invoice> invoices, Function<Invoice, BigDecimal> amount) {
        return invoices.stream()
            .map(amount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int missingAmountCount(List<Invoice> invoices) {
        return (int) invoices.stream().filter(invoice -> invoice.getAmountIncVat() == null).count();
    }
}
