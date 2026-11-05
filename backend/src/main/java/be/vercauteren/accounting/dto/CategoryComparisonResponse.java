package be.vercauteren.accounting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Depenses par categorie, annee contre annee. Quand {@code until} est donne,
 * les deux annees sont coupees au meme jour du calendrier (periode comparable).
 */
public record CategoryComparisonResponse(
    int year,
    int previousYear,
    LocalDate until,
    List<CategoryComparisonRow> rows,
    CategoryComparisonRow total,
    int missingAmountCount) {
}
