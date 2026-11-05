package be.vercauteren.accounting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Fournisseurs apparus ou disparus d'une annee a l'autre.
 *
 * <ul>
 *   <li>{@code newSuppliers}: factures sur {@code year} (jusqu'a {@code until}),
 *       aucune sur toute l'annee precedente;</li>
 *   <li>{@code missingSuppliers}: factures sur {@code previousYear} (jusqu'au meme
 *       jour), aucune sur toute l'annee courante.</li>
 * </ul>
 */
public record SupplierChangesResponse(
    int year,
    int previousYear,
    LocalDate until,
    List<SupplierSummary> newSuppliers,
    List<SupplierSummary> missingSuppliers) {
}
