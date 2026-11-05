import type { ExpenseCategory } from './supplier.model';

/** Somme d'un groupe: `count` compte toutes les factures, montant manquant compris. */
export interface AnalysisTotal {
  amountIncVat: number;
  amountExVat: number;
  count: number;
}

export interface CategoryComparisonRow {
  /** `null`: fournisseurs sans catégorie (ou ligne de total). */
  category: ExpenseCategory | null;
  current: AnalysisTotal;
  previous: AnalysisTotal;
}

export interface CategoryComparisonResponse {
  year: number;
  previousYear: number;
  until: string | null;
  rows: CategoryComparisonRow[];
  total: CategoryComparisonRow;
  missingAmountCount: number;
}

export interface SupplierSummary {
  supplierId: number;
  supplierName: string;
  category: ExpenseCategory | null;
  count: number;
  amountIncVat: number;
  amountExVat: number;
  firstDate: string;
  lastDate: string;
}

export interface SupplierChangesResponse {
  year: number;
  previousYear: number;
  until: string | null;
  newSuppliers: SupplierSummary[];
  missingSuppliers: SupplierSummary[];
}

export interface SupplierInventoryResponse {
  year: number;
  category: ExpenseCategory | null;
  suppliers: SupplierSummary[];
  total: AnalysisTotal;
  missingAmountCount: number;
}
