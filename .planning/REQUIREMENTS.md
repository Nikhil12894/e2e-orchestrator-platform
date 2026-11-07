# Requirements: Standalone DB Export & Form URL-Encoded Requests

**Defined:** 2026-11-06  
**Core Value:** Ensure complete functional parity and execution fidelity between visual workflows, dynamic in-memory Karate execution, and standalone exported Karate `.feature` files for all step types including database polling and form-parameter HTTP requests.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Database Polling Karate Export

- [x] **DBEXP-01**: Standalone Karate feature exporter generates self-contained inline JDBC functions using `java.sql.DriverManager` in the feature Background instead of a comment placeholder.
- [x] **DBEXP-02**: Standalone Karate export includes database polling retry loops respecting `pollIntervalMs` and `maxAttempts` from the visual step configuration.
- [x] **DBEXP-03**: Standalone Karate export generates automated assertions validating expected row counts or custom SQL column conditions (`row.status == 'COMPLETED'`).
- [x] **DBEXP-04**: Standalone Karate export generates variable extraction statements from matched database query rows (`* def var = result.rows[0].col`) for downstream steps.

### HTTP Form URL-Encoded Requests

- [x] **FORM-01**: Node Configuration Drawer provides a Body Type selector (`JSON` vs `x-www-form-urlencoded`) when HTTP method is `POST`, `PUT`, or `PATCH`.
- [x] **FORM-02**: Node Configuration Drawer provides an interactive key-value table editor supporting parameter key/value pairs, variable insertion (`${var}`), and auto-synchronizing `Content-Type: application/x-www-form-urlencoded`.
- [x] **FORM-03**: Dynamic Karate execution engine (`execute-http.feature`) branches on form-encoded body type to natively invoke Karate's `form fields` keyword without passing a raw JSON `request` body.
- [x] **FORM-04**: Standalone Karate feature exporter generates native `form fields <payload>` statements instead of `request` when exporting form-encoded HTTP steps.

## v2 Requirements

Deferred to future releases. Tracked but not in current roadmap.

### Advanced Form Payloads

- **FORM-V2-01**: Support `multipart/form-data` with binary file attachments in visual drawer and Karate execution.
- **FORM-V2-02**: Support raw URL-encoded string mode with live syntax validation and variable extraction.

### Advanced Database Assertions

- **DBEXP-V2-01**: Support multi-table join validation and JSON column assertion operators in exported features.

## Out of Scope

Explicitly excluded to maintain stability and prevent scope creep.

| Feature | Reason |
|---------|--------|
| Custom External Java Class Dependencies for DB Export | Exported `.feature` files must run in any standard Karate runner with only standard JDBC drivers |
| Multipart file uploads | Distinct protocol requiring binary multipart MIME handling; deferred to v2 |
| Automatic database schema migration tools | DB polling is for test verification, not schema lifecycle management |

## Traceability

Which phases cover which requirements. Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DBEXP-01 | Phase 1 | Complete |
| DBEXP-02 | Phase 1 | Complete |
| DBEXP-03 | Phase 1 | Complete |
| DBEXP-04 | Phase 1 | Complete |
| FORM-01 | Phase 2 | Complete |
| FORM-02 | Phase 2 | Complete |
| FORM-03 | Phase 2 | Complete |
| FORM-04 | Phase 2 | Complete |

**Coverage:**
- v1 requirements: 8 total
- Mapped to phases: 8
- Unmapped: 0

---
*Requirements defined: 2026-11-06*
*Last updated: 2026-11-06 after roadmap creation*
