package be.vercauteren.accounting.dto;

import java.math.BigDecimal;

/**
 * Somme d'un groupe de factures. Les montants manquants ne sont pas comptes
 * comme zero: {@code count} compte toutes les factures du groupe, et la reponse
 * qui l'englobe dit combien n'avaient pas de montant.
 */
public record AnalysisTotal(BigDecimal amountIncVat, BigDecimal amountExVat, int count) {
}
