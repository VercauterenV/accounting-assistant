import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AnalysisService } from '../services/analysis.service';
import {
  CategoryComparisonResponse,
  CategoryComparisonRow,
  SupplierChangesResponse,
  SupplierInventoryResponse,
} from '../models/analysis.model';
import { EXPENSE_CATEGORY_LABELS, ExpenseCategory } from '../models/supplier.model';

export type AnalysisTab = 'categories' | 'changes' | 'inventory';

/** Variation d'une année à l'autre, prête à afficher. */
export interface Delta {
  direction: 'up' | 'down' | 'flat';
  amount: number;
  /** `null` quand l'année précédente est à zéro: on affiche "nouveau". */
  percent: number | null;
}

/**
 * Écran "Analyse": les achats d'une année comparés à l'année précédente.
 * Tous les montants viennent de `/api/analysis`, déjà sommés côté serveur.
 */
@Component({
  selector: 'app-analysis',
  imports: [FormsModule, CurrencyPipe, DatePipe, NgTemplateOutlet],
  templateUrl: './analysis.html',
  styleUrl: './analysis.scss',
})
export class Analysis implements OnInit {
  private readonly analysisService = inject(AnalysisService);

  readonly currentYear = new Date().getFullYear();
  readonly years: number[] = [];
  readonly categories = (Object.keys(EXPENSE_CATEGORY_LABELS) as ExpenseCategory[])
    .map(value => ({ value, label: EXPENSE_CATEGORY_LABELS[value] }));

  tab = signal<AnalysisTab>('categories');
  year = signal(this.currentYear);
  /** Coupe les deux années au même jour: n'a de sens que pour l'année en cours. */
  comparable = signal(true);
  inventoryCategory = signal<ExpenseCategory | null>(null);

  loading = signal(false);
  error = signal<string | null>(null);
  categoryData = signal<CategoryComparisonResponse | null>(null);
  changesData = signal<SupplierChangesResponse | null>(null);
  inventoryData = signal<SupplierInventoryResponse | null>(null);

  readonly isCurrentYear = computed(() => this.year() === this.currentYear);
  readonly until = computed(() => (this.isCurrentYear() && this.comparable() ? todayIso() : null));

  /** Plus grand montant affiché: la largeur des barres est relative à lui. */
  readonly maxAmount = computed(() => {
    const rows = this.categoryData()?.rows ?? [];
    return rows.reduce(
      (max, row) => Math.max(max, row.current.amountIncVat, row.previous.amountIncVat), 0);
  });

  ngOnInit(): void {
    for (let y = this.currentYear; y >= 2024; y--) {
      this.years.push(y);
    }
    this.load();
  }

  selectTab(tab: AnalysisTab): void {
    this.tab.set(tab);
    this.load();
  }

  setYear(year: number): void {
    this.year.set(year);
    this.load();
  }

  setComparable(comparable: boolean): void {
    this.comparable.set(comparable);
    this.load();
  }

  setInventoryCategory(category: ExpenseCategory | null): void {
    this.inventoryCategory.set(category);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const done = { complete: () => this.loading.set(false), error: () => this.fail() };
    switch (this.tab()) {
      case 'categories':
        this.analysisService.compareCategories(this.year(), this.until())
          .subscribe({ next: data => this.categoryData.set(data), ...done });
        break;
      case 'changes':
        this.analysisService.compareSuppliers(this.year(), this.until())
          .subscribe({ next: data => this.changesData.set(data), ...done });
        break;
      case 'inventory':
        this.analysisService.inventory(this.year(), this.inventoryCategory())
          .subscribe({ next: data => this.inventoryData.set(data), ...done });
        break;
    }
  }

  categoryLabel(category: ExpenseCategory | null): string {
    return category ? EXPENSE_CATEGORY_LABELS[category] : 'Sans catégorie';
  }

  barWidth(amount: number): string {
    const max = this.maxAmount();
    return max > 0 ? `${(amount / max) * 100}%` : '0%';
  }

  delta(row: CategoryComparisonRow): Delta {
    const diff = row.current.amountIncVat - row.previous.amountIncVat;
    const previous = row.previous.amountIncVat;
    return {
      direction: diff > 0 ? 'up' : diff < 0 ? 'down' : 'flat',
      amount: Math.abs(diff),
      percent: previous > 0 ? Math.round((Math.abs(diff) / previous) * 100) : null,
    };
  }

  private fail(): void {
    this.loading.set(false);
    this.error.set("Impossible de charger l'analyse.");
  }
}

/** Date du jour en heure locale (pas UTC), au format ISO. */
export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}
