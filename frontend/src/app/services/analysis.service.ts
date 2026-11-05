import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CategoryComparisonResponse,
  SupplierChangesResponse,
  SupplierInventoryResponse,
} from '../models/analysis.model';
import { ExpenseCategory } from '../models/supplier.model';

/** Agrégats calculés côté serveur: le frontend n'additionne rien. */
@Injectable({ providedIn: 'root' })
export class AnalysisService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/analysis';

  compareCategories(year: number, until: string | null): Observable<CategoryComparisonResponse> {
    return this.http.get<CategoryComparisonResponse>(`${this.url}/categories`, {
      params: periodParams(year, until),
    });
  }

  compareSuppliers(year: number, until: string | null): Observable<SupplierChangesResponse> {
    return this.http.get<SupplierChangesResponse>(`${this.url}/suppliers/changes`, {
      params: periodParams(year, until),
    });
  }

  inventory(year: number, category: ExpenseCategory | null): Observable<SupplierInventoryResponse> {
    const params: Record<string, string> = { year: String(year) };
    if (category) {
      params['category'] = category;
    }
    return this.http.get<SupplierInventoryResponse>(`${this.url}/suppliers`, { params });
  }
}

function periodParams(year: number, until: string | null): Record<string, string> {
  const params: Record<string, string> = { year: String(year) };
  if (until) {
    params['until'] = until;
  }
  return params;
}
