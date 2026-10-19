# Recommendation System Architecture & Specification

## Overview
This document specifies the rule-based book recommendation engine implemented for the EBookStore capstone (`GET /api/recommendations`). The recommendation engine is designed without external machine learning dependencies, providing reliable, deterministic, and fast personalized recommendations based on previous order history with a popular fallback strategy.

---

## 1. Endpoint Contract

- **Method**: `GET`
- **Path**: `/api/recommendations`
- **Authentication**: Required (`Bearer` JWT)
- **Query Parameters**:
  - `limit`: Optional integer, `min = 1`, `max = 20`, `default = 10`.

### Response Schema (`RecommendationList`)
```json
{
  "strategy": "PURCHASE_HISTORY" | "POPULAR",
  "books": [
    {
      "id": 2,
      "title": "Effective Java",
      "authors": ["Joshua Bloch"],
      "isbn": "9780134685991",
      "price": 799.00,
      "imageUrl": "https://example.com/effective-java.jpg",
      "ratingAvg": 4.80,
      "ratingCount": 120,
      "inStock": true,
      "categoryId": 1,
      "categoryName": "Technology",
      "publisherId": 2,
      "publisherName": "Addison-Wesley",
      "publishedYear": 2018
    }
  ]
}
```

---

## 2. Recommendation Logic & Strategies

### Strategy 1: `PURCHASE_HISTORY` (Personalized)
When the authenticated user has non-cancelled orders (`status != 'CANCELLED'`), recommendations are derived from their purchase history:

1. **Extraction of Affinity Signals**:
   - Collect distinct `book_id`s from non-cancelled order items.
   - Extract the set of categories (`categoryIds`), publishers (`publisherIds`), and authors (`authorIds`) across these purchased books.

2. **Candidate Filtering & Selection**:
   - Must be active (`active = true`).
   - Must be in stock (`stock_quantity > 0`).
   - Excludes books the user already purchased (`id NOT IN (purchasedBookIds)`).
   - Matches at least one affinity signal (same category, publisher, or author).

3. **Relevance Ranking**:
   - Priority 1: Books sharing an author with previously purchased books.
   - Priority 2: Books sharing a category.
   - Priority 3: Books sharing a publisher.
   - Tie-breaking: Publisher match, then `rating_avg DESC`, `rating_count DESC`, and `id ASC` for deterministic order.

### Strategy 2: `POPULAR` (Fallback)
The engine automatically falls back to `strategy: POPULAR` under the following conditions:
- The user has no prior orders.
- All previous orders were cancelled.
- The personalized query yields 0 candidate books (e.g. all related items are out of stock or already purchased).

Popularity is ranked by:
1. `rating_avg DESC`
2. `rating_count DESC`
3. `id ASC`

Excludes previously purchased books when available, returning active, in-stock books.

---

## 3. Privacy & Security Safeguards

- **User Isolation**: Order history queries are strictly scoped to `userId` from the verified JWT token (`AuthenticatedUser`). No cross-user purchase data is exposed.
- **Data Protection**: Only public book summary fields (`BookSummary`) are exposed; internal inventory thresholds and version locks are kept server-side.
- **Input Validation**: Limits are bounded between 1 and 20. Out-of-bounds requests return `400 VALIDATION_FAILED`.
