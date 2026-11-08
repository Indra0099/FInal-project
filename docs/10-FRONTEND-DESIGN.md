# Frontend design — ShardQuery workbench

Updated 8 November 2026. This redesign gives ShardQuery its own database-workbench identity, replacing the earlier sage sidebar layout.

## Visual system

| Role | Color | Use |
| --- | --- | --- |
| Midnight blue | `#14213D` | Application header and brand |
| Cobalt | `#2454CE` | Run query, selected controls and editor border |
| Orange | `#F29A62` | Brand mark and active navigation indicator |
| Pale blue-gray | `#F1F4F9` | Workspace background |
| White | `#FFFFFF` | Editor, upload form and result surfaces |
| Ink | `#1B2941` | Main text |

The design uses system sans-serif fonts, compact monospace labels, restrained borders, and smaller corner radii. Fonts and artwork require no external service. Lucide icons and the CSS brand mark are bundled with the frontend.

## Screen structure

1. **Application shell:** horizontal navigation for Query workspace, Document library, History and How it works. The local-workspace indicator uses actual database health.
2. **Query workspace:** editor and output on the left; data connections and example queries on the right. Example buttons fill the question and select its mode before execution.
3. **Connected sources:** three database labels reflect health responses. The indexed-document count comes from the documents API. Connection lines illustrate query association; they are not a live traffic animation.
4. **Results:** Answer, Records and Sources tabs retain the same API data. Execution details remain expandable. Saved results are still available in history.
5. **Document library:** a focused upload form sits beside the document list. Existing product/customer association fields, status badges, retries and downloads remain available.
6. **How it works:** a dark four-step workflow strip and readable setup and status sections.

Below 850 pixels the query columns stack. The 390-pixel layout keeps the query modes and full-width Run query button visible. Tables scroll inside their own container. Keyboard focus outlines, input labels, a skip-to-workspace link and reduced-motion preferences are supported. The skip link focuses the main region without changing the hash-router URL.

## Files and responsibilities

- `frontend/src/App.jsx`: shell, screen structure, shared components and existing interactions. `QueryContext` reads health/document metadata and supplies example actions.
- `frontend/src/styles.css`: palette, spacing, layouts, controls and responsive rules.
- `frontend/index.html`: browser title and theme color.
- `frontend/src/api.js`: existing Axios connection; no API contract change was required.

## Development and local build

1. Start the complete project using [Local setup](05-LOCAL-SETUP.md).
2. For frontend development, keep the backend at port 8087 and run:

   ```sh
   cd frontend
   npm ci
   npm run dev
   ```

3. Open `http://127.0.0.1:5178`. Vite forwards `/api` calls to the local backend.
4. Edit components and styles. Confirm navigation, mode selection, loading states, actual query results and responsive layouts.
5. Check the production build:

   ```sh
   npm run build
   ```

6. From the project root, package the frontend into the Spring Boot application:

   ```sh
   docker compose up --build -d app
   ```

   On the original development Mac, use `docker --context colima-shardquery compose up --build -d app` to select this project's isolated Docker context.

7. Open `http://localhost:8087`. Refresh any page that was open before rebuilding.

## Verification performed

- Vite production build and Docker build completed successfully.
- Firefox displayed the new packaged application at port 8087.
- An example filled the editor, selected Database mode and submitted a real Gemini query. The job completed with three matching laptop products and no warnings.
- History reopened the completed query; the Records tab displayed the three returned products and INR prices.
- The document library displayed five READY documents and the upload form.
- A previous completed hybrid query displayed all four cited source excerpts in the redesigned Sources tab.
- The workflow/setup route and skip-to-workspace link were checked.
- Desktop and 390 × 844 responsive views were visually inspected. The mobile check used Firefox responsive mode, not a physical phone.
- No new file upload was performed for this layout change; the existing upload implementation was retained.

The backend and Gemini integration were not changed for this redesign. The earlier full 32-test integration result remains recorded separately in [Verification](VERIFICATION.md).

## Screenshots

[Packaged desktop workspace](screenshots/workbench-home.png) · [390-pixel mobile preview](screenshots/workbench-mobile.png)

## Account and database screens
The same navy/cobalt palette extends to a split sign-in/register screen and a database editor with a compact database list, table tabs, a column builder and a paginated record grid. Small screens stack the database list above the editor; wide record tables scroll inside their container. The top navigation now includes My databases and an account/sign-out control. See [screenshots and steps](11-ACCOUNTS-AND-DATABASES.md).
