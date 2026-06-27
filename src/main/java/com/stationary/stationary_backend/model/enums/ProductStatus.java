package com.stationary.stationary_backend.model.enums;

/**
 * ProductStatus — Enum for product visibility state.
 *
 * WHY an enum over a boolean isDeleted?
 * ──────────────────────────────────────────────────────────
 * Your original code used: boolean deleted = false
 * That works for now but has a scaling problem.
 *
 * What if you need a third state later?
 *   - DRAFT    (admin saved but not published yet)
 *   - OUT_OF_STOCK (auto-set when stock hits 0)
 *   - DISCONTINUED (different from deleted)
 *
 * With a boolean, you'd need to add MORE booleans (isPublished,
 * isDiscontinued...) — and their combinations create ambiguity.
 * With an enum, adding DRAFT is one line here. Zero refactor.
 *
 * WHY ACTIVE/INACTIVE naming vs PUBLISHED/DELETED?
 * INACTIVE is reversible — admin can toggle back to ACTIVE.
 * "DELETED" implies permanence. We want soft delete, not hard delete.
 * Naming matters for clarity when reading query filters.
 *
 * STORED IN MONGODB AS: the string "ACTIVE" or "INACTIVE"
 * (Spring Data MongoDB serializes enums as strings by default — readable)
 *
 * FUTURE (v2):
 * Add DRAFT here when admin dashboard needs a "save for later" feature.
 * Existing ACTIVE/INACTIVE records are unaffected.
 */
public enum ProductStatus {

    /**
     * Product is live and visible to public browsing.
     * All GET /api/v1/products queries include ACTIVE products.
     */
    ACTIVE,

    /**
     * Product is hidden from public but retained in DB.
     * Used as soft delete — admin can reactivate.
     * All public queries filter WHERE status = 'ACTIVE'.
     */
    INACTIVE
}
