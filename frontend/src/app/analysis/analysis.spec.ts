import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Analysis, todayIso } from './analysis';
import { AnalysisService } from '../services/analysis.service';
import {
  AnalysisTotal,
  CategoryComparisonResponse,
  SupplierChangesResponse,
  SupplierInventoryResponse,
  SupplierSummary,
} from '../models/analysis.model';

const total = (amountIncVat: number, count = 1, amountExVat = amountIncVat): AnalysisTotal =>
  ({ amountIncVat, amountExVat, count });

const summary = (supplierId: number, supplierName: string, amountIncVat: number): SupplierSummary => ({
  supplierId,
  supplierName,
  category: supplierId === 1 ? 'RESTAURANT' : null,
  count: 2,
  amountIncVat,
  amountExVat: amountIncVat,
  firstDate: '2026-02-01',
  lastDate: '2026-05-01',
});

const categories = (until: string | null): CategoryComparisonResponse => ({
  year: 2026,
  previousYear: 2025,
  until,
  rows: [
    { category: 'TELECOM', current: total(200), previous: total(300) },
    { category: 'RESTAURANT', current: total(150), previous: total(100, 2) },
    { category: null, current: total(10), previous: total(0, 0) },
    { category: 'AUTRE', current: total(5), previous: total(5) },
  ],
  total: { category: null, current: total(365, 4), previous: total(405, 4) },
  missingAmountCount: 1,
});

const changes = (until: string | null): SupplierChangesResponse => ({
  year: 2026,
  previousYear: 2025,
  until,
  newSuppliers: [summary(1, 'Brasserie du Coin', 40)],
  missingSuppliers: [],
});

const inventory = (missingAmountCount: number): SupplierInventoryResponse => ({
  year: 2026,
  category: null,
  suppliers: [summary(1, 'Brasserie du Coin', 50), summary(2, 'Divers SA', 20)],
  total: total(70, 4, 58),
  missingAmountCount,
});

describe('Analysis', () => {
  let service: Record<string, ReturnType<typeof vi.fn>>;
  let fixture: ComponentFixture<Analysis>;
  let component: Analysis;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    service = {
      compareCategories: vi.fn((_year: number, until: string | null) => of(categories(until))),
      compareSuppliers: vi.fn((_year: number, until: string | null) => of(changes(until))),
      inventory: vi.fn(() => of(inventory(1))),
    };
    TestBed.configureTestingModule({ providers: [{ provide: AnalysisService, useValue: service }] });
    fixture = TestBed.createComponent(Analysis);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  it('opens on the categories of the current year, cut on today for both years', () => {
    expect(component.years[0]).toBe(component.currentYear);
    expect(component.years.at(-1)).toBe(2024);
    expect(service['compareCategories']).toHaveBeenCalledWith(component.currentYear, todayIso());
    expect(text()).toContain('du 1er janvier au');
    expect(text()).toContain('1 facture(s) sans montant');
    expect(text()).toContain('Sans catégorie');
    expect(text()).toContain('Télécom');
    expect(text()).toContain('nouveau');
    expect(text()).toContain('Total');
  });

  it('computes the deltas and the bar widths from the server totals', () => {
    const rows = component.categoryData()!.rows;
    expect(component.delta(rows[0])).toEqual({ direction: 'down', amount: 100, percent: 33 });
    expect(component.delta(rows[1])).toEqual({ direction: 'up', amount: 50, percent: 50 });
    expect(component.delta(rows[2])).toEqual({ direction: 'up', amount: 10, percent: null });
    expect(component.delta(rows[3])).toEqual({ direction: 'flat', amount: 0, percent: 0 });
    expect(component.barWidth(300)).toBe('100%');
    expect(component.barWidth(150)).toBe('50%');
    expect(component.categoryLabel('RESTAURANT')).toBe('Restaurant');
    expect(component.categoryLabel(null)).toBe('Sans catégorie');
  });

  it('compares full years when the period is not comparable or the year is past', () => {
    component.setComparable(false);
    expect(service['compareCategories']).toHaveBeenLastCalledWith(component.currentYear, null);
    fixture.detectChanges();
    expect(text()).toContain('années complètes');

    component.setComparable(true);
    component.setYear(2025);
    expect(service['compareCategories']).toHaveBeenLastCalledWith(2025, null);
    fixture.detectChanges();
    expect(text()).not.toContain('Période comparable');
  });

  it('draws empty bars and an empty table when nothing was bought', () => {
    service['compareCategories'].mockReturnValue(of({
      ...categories(null), rows: [], total: { category: null, current: total(0, 0), previous: total(0, 0) },
      missingAmountCount: 0,
    }));
    component.load();
    fixture.detectChanges();
    expect(component.maxAmount()).toBe(0);
    expect(component.barWidth(0)).toBe('0%');
    expect(text()).toContain('Aucun achat sur ces deux années.');
    expect(text()).not.toContain('sans montant');
  });

  it('lists new and missing suppliers', () => {
    component.selectTab('changes');
    fixture.detectChanges();
    expect(service['compareSuppliers']).toHaveBeenCalledWith(component.currentYear, todayIso());
    expect(text()).toContain("Jusqu'au");
    expect(text()).toContain('Brasserie du Coin');
    expect(text()).toContain('Aucun fournisseur absent.');

    component.setComparable(false);
    fixture.detectChanges();
    expect(text()).toContain('Années complètes');
  });

  it('lists the purchases by supplier, filtered on a category', () => {
    component.selectTab('inventory');
    fixture.detectChanges();
    expect(service['inventory']).toHaveBeenCalledWith(component.currentYear, null);
    expect(text()).toContain('2 fournisseur(s), 4 facture(s)');
    expect(text()).toContain('1 facture(s) sans montant');
    expect(text()).toContain('Divers SA');

    service['inventory'].mockReturnValue(of({ ...inventory(0), suppliers: [] }));
    component.setInventoryCategory('RESTAURANT');
    fixture.detectChanges();
    expect(service['inventory']).toHaveBeenLastCalledWith(component.currentYear, 'RESTAURANT');
    expect(text()).toContain('Aucun achat.');
    expect(text()).not.toContain('sans montant');
  });

  it('says so when the analysis cannot be loaded', () => {
    service['compareCategories'].mockReturnValue(throwError(() => new Error('boom')));
    component.load();
    fixture.detectChanges();
    expect(component.loading()).toBe(false);
    expect(text()).toContain("Impossible de charger l'analyse.");
  });

  it('has no bar to scale before the first answer', () => {
    const fresh = TestBed.createComponent(Analysis).componentInstance;
    expect(fresh.maxAmount()).toBe(0);
  });

  it('formats today in local time', () => {
    expect(todayIso(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
    expect(todayIso(new Date(2026, 10, 25))).toBe('2026-11-25');
  });
});
