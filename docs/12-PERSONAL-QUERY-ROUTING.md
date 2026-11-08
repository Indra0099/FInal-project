# Personal database query routing

## Fix contract

Previously an omitted `tableId` always selected the sample commerce planner. Questions about user-created schemas consequently failed with a commerce-only error. The default source now discovers the signed-in account's table catalog before choosing the query path.

`POST /api/queries` accepts optional `source`: `AUTO` (default), `PERSONAL`, or `DEMO`. Existing explicit `tableId` requests remain supported. Explicit guided sample plans stay on the demo path. Documents mode continues to search only the account's uploaded documents.

- AUTO: choose relevant personal tables, shared sample commerce data, or documents from the question and available schema.
- PERSONAL: use the account's tables only; never fall back to sample products/customers/orders.
- DEMO: explicitly use the fictional commerce dataset.
- tableId: restrict the question to that owned table.

Personal queries can select/filter/sort one table or perform one inner equality join between two owned tables, including tables in different personal databases. A student/fees question can associate `students.student.id` with `Students fees.student fees.student_id`, then apply the numeric fees filter. Ambiguous relationships must produce a clarification message instead of a guessed join.

The AI returns table UUIDs, column names, typed filters, one join, sorting and a limit. It never returns executable SQL. The backend validates every reference against the owner's catalog before opening a connection. Single-table queries now push filters and sorting into bounded SQL. For two-table association, each selected table is read in a read-only transaction with a 1,001-row sentinel bound; exceeding 1,000 source rows fails explicitly. Application-side association is capped at 5,000 intermediate rows. Filtering, sorting and the final 1–100 result limit follow association. No tables or records are modified.

Personal results expose selected table metadata and `dataSource: PERSONAL`; saved queries restore the right source. Personal and auto-source requests bypass the result cache, preventing stale catalog/routing decisions. Demo-only results retain their existing short-lived cache.

Unsupported: arbitrary SQL, writes through chat, aggregates, more than two tables, outer joins, fuzzy joins, or a globally consistent snapshot across databases. Separate reads and the application join are visible in execution traces. Source limits or unavailable databases do not silently become empty or complete answers.

## User steps

1. Sign in and open Query workspace.
2. Leave the source on **Automatically choose**, or choose **My databases** to exclude the sample commerce data.
3. Ask, for example: **Find the students whose fees are above ₹150000.**
4. Inspect **Data used**, **Records** and the execution details.
5. For a single table, select it explicitly or use **Ask about this table** in My databases.

The implementation follows this contract. See [verification evidence](VERIFICATION.md), [the live fictional student result](sample-student-fees-result.json), and [the workflow diagram](diagrams/14-personal-auto-query.mmd). Refresh an already-open browser tab after updating the application so it loads the new source selector.

Connected PostgreSQL tables also participate in this catalog. See [connection setup](13-DATABASE-MANAGEMENT-AND-CONNECTIONS.md).
