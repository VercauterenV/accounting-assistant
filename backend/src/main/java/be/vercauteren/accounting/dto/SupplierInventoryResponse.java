package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.ExpenseCategory;
import java.util.List;

/** Inventaire des achats d'une annee, par fournisseur, eventuellement filtre sur une categorie. */
public record SupplierInventoryResponse(
    int year,
    ExpenseCategory category,
    List<SupplierSummary> suppliers,
    AnalysisTotal total,
    int missingAmountCount) {
}
