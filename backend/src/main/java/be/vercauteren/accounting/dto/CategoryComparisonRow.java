package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.ExpenseCategory;

/** Une categorie sur deux annees. {@code category} est nul pour les fournisseurs sans categorie. */
public record CategoryComparisonRow(ExpenseCategory category, AnalysisTotal current, AnalysisTotal previous) {
}
