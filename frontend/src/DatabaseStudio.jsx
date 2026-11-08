import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  Database,
  Plus,
  Table2,
  ArrowRight,
  Pencil,
  X,
  Loader2,
  RefreshCw,
  Trash2,
  Link2,
  Settings2,
} from "lucide-react";
import { api, errorMessage, label } from "./api";
import { DatabaseActionDialog, ConnectionForm } from "./DatabaseActions";
const blankColumn = () => ({ name: "", type: "TEXT", required: false });
export default function DatabaseStudio() {
  const [databases, setDatabases] = useState([]),
    [databaseId, setDatabaseId] = useState(""),
    [tables, setTables] = useState([]),
    [tableId, setTableId] = useState("");
  const [databaseName, setDatabaseName] = useState(""),
    [tableName, setTableName] = useState(""),
    [columns, setColumns] = useState([blankColumn()]);
  const [page, setPage] = useState(0),
    [data, setData] = useState(null),
    [values, setValues] = useState({}),
    [editing, setEditing] = useState(null);
  const [showTable, setShowTable] = useState(false),
    [showRow, setShowRow] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [revision, setRevision] = useState(0);
  const [management, setManagement] = useState(null);
  const [connection, setConnection] = useState(null);
  const [showColumns, setShowColumns] = useState(false);
  const selectedDb = databases.find((d) => d.id === databaseId),
    table = tables.find((t) => t.id === tableId);
  useEffect(() => {
    let active = true;
    api
      .get("/databases")
      .then((r) => {
        if (active) setDatabases(r.data);
      })
      .catch((e) => active && setError(errorMessage(e)));
    return () => {
      active = false;
    };
  }, [revision]);
  useEffect(() => {
    if (!databaseId) {
      setTables([]);
      setTableId("");
      return;
    }
    let active = true;
    api
      .get(`/databases/${databaseId}/tables`)
      .then((r) => {
        if (active) {
          setTables(r.data);
          setTableId((id) => (r.data.some((t) => t.id === id) ? id : ""));
        }
      })
      .catch((e) => active && setError(errorMessage(e)));
    return () => {
      active = false;
    };
  }, [databaseId, revision]);
  useEffect(() => {
    setTableId("");
    setTables([]);
    setShowRow(false);
    setShowTable(false);
    setShowColumns(false);
    setPage(0);
  }, [databaseId]);
  useEffect(() => {
    setData(null);
    if (!tableId) return;
    let active = true;
    api
      .get(`/databases/${databaseId}/tables/${tableId}/rows?page=${page}`)
      .then((r) => active && setData(r.data))
      .catch((e) => active && setError(errorMessage(e)));
    return () => {
      active = false;
    };
  }, [databaseId, tableId, page, revision]);
  async function action(fn) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await fn();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  function chooseTable(id) {
    setTableId(id);
    setPage(0);
    setShowTable(false);
    setShowRow(false);
    setError("");
    setMessage("");
  }
  function openRow(row) {
    setEditing(row?._row_id ?? null);
    setValues(
      Object.fromEntries(
        table.columns.map((c) => [
          c.name,
          row?.[c.name] == null ? "" : String(row[c.name]),
        ]),
      ),
    );
    setShowRow(true);
    setError("");
  }
  async function saveRow(e) {
    e.preventDefault();
    await action(async () => {
      const path = `/databases/${databaseId}/tables/${tableId}/rows`;
      const body = {
        values: Object.fromEntries(
          Object.entries(values).map(([k, v]) => [k, v === "" ? null : v]),
        ),
      };
      if (editing === null) await api.post(path, body);
      else await api.put(`${path}/${editing}`, body);
      setShowRow(false);
      setRevision((r) => r + 1);
      setMessage(editing === null ? "Row added." : "Changes saved.");
    });
  }
  const remote = selectedDb?.kind === "EXTERNAL";
  function manage(title, method, path, name, extra = {}) {
    setManagement({ title, method, path, name, ...extra });
  }
  async function afterManagement(a) {
    if (a.method === "delete" && a.path === `/databases/${databaseId}`) {
      setDatabases((current) => current.filter((d) => d.id !== databaseId));
      setDatabaseId("");
      setTables([]);
      setTableId("");
      setData(null);
    } else if (
      a.method === "delete" &&
      a.path === `/databases/${databaseId}/tables/${tableId}`
    ) {
      setTables((current) => current.filter((t) => t.id !== tableId));
      setDatabases((current) =>
        current.map((d) =>
          d.id === databaseId
            ? { ...d, tableCount: Math.max(0, d.tableCount - 1) }
            : d,
        ),
      );
      setTableId("");
      setData(null);
    }
    setError("");
    setShowRow(false);
    setPage(0);
    setRevision((r) => r + 1);
    setMessage(
      a.disconnect
        ? "Connection removed. Remote data was not changed."
        : a.method === "delete"
          ? `${a.title.replace(/^Delete /, "")} “${a.name}” deleted.`
          : "Changes saved.",
    );
  }
  const tablePath = `/databases/${databaseId}/tables/${tableId}`;
  return (
    <>
      {management && (
        <DatabaseActionDialog
          key={management.path + management.method}
          action={management}
          onClose={() => setManagement(null)}
          onDone={afterManagement}
        />
      )}
      <div className="page-title">
        <div>
          <span className="eyebrow">BUILD YOUR OWN DATASET</span>
          <h1>My databases</h1>
          <p>
            Create a database, define your columns and start adding records. No
            SQL needed. Connect an existing PostgreSQL database to query its
            live tables.
          </p>
        </div>
      </div>
      <div className="database-top-actions">
        <button
          className="primary"
          onClick={() => setConnection({})}
          disabled={busy || !!connection}
        >
          <Link2 size={16} /> Connect PostgreSQL
        </button>
      </div>
      {connection && (
        <ConnectionForm
          key={connection.id || "new"}
          database={connection.id ? connection : null}
          onCancel={() => setConnection(null)}
          onDone={async (d) => {
            setConnection(null);
            setDatabaseId(d.id);
            setRevision((r) => r + 1);
            setMessage(
              "Connected. Select a table or ask a question in Query workspace.",
            );
          }}
        />
      )}
      {error && (
        <div className="notice" role="alert">
          {error}
        </div>
      )}
      {message && (
        <p className="save-message" role="status">
          {message}
        </p>
      )}
      <div className="database-layout">
        <aside className="database-sidebar">
          <h2>
            <Database size={18} /> Your databases
          </h2>
          <p>Private to your account</p>
          <div className="database-list">
            {databases.map((d) => (
              <div key={d.id}>
                <button
                  disabled={busy}
                  className={d.id === databaseId ? "selected" : ""}
                  onClick={() => {
                    setDatabaseId(d.id);
                    setError("");
                    setMessage("");
                  }}
                >
                  <Database size={17} />
                  <span>
                    {d.name}
                    <small>
                      {d.status === "READY"
                        ? `${d.tableCount} ${d.tableCount === 1 ? "table" : "tables"}${d.kind === "EXTERNAL" ? " · Connected" : ""}`
                        : label(d.status)}
                    </small>
                  </span>
                </button>
                {d.status === "FAILED" && d.kind !== "EXTERNAL" && (
                  <button
                    onClick={() =>
                      action(async () => {
                        await api.post(`/databases/${d.id}/retry`);
                        setRevision((r) => r + 1);
                      })
                    }
                  >
                    <RefreshCw size={14} /> Retry creation
                  </button>
                )}
              </div>
            ))}
          </div>
          <form
            className="stack-form new-database"
            onSubmit={(e) => {
              e.preventDefault();
              action(async () => {
                const r = await api.post("/databases", {
                  name: databaseName.trim(),
                });
                setDatabaseName("");
                setRevision((v) => v + 1);
                setDatabaseId(r.data.id);
                setMessage("Database created. Add your first table.");
              });
            }}
          >
            <label>
              New database
              <input
                value={databaseName}
                onChange={(e) => setDatabaseName(e.target.value)}
                required
                pattern="[A-Za-z][A-Za-z0-9 _\-]{0,59}"
                maxLength={60}
                placeholder="e.g. College records"
              />
            </label>
            <button
              className="primary"
              disabled={
                busy ||
                databases.filter((d) => d.kind !== "EXTERNAL").length >= 3
              }
            >
              {busy ? (
                <Loader2 size={15} className="spin" />
              ) : (
                <Plus size={15} />
              )}{" "}
              Create database
            </button>
            <small>
              Up to 3 databases per account. Start the name with a letter.
            </small>
          </form>
        </aside>
        <section className="database-content">
          {!selectedDb ? (
            <div className="database-empty">
              <Database size={36} />
              <h2>A home for your data</h2>
              <p>
                Create a database on the left, then add a table such as
                students, expenses or products.
              </p>
              <div className="database-steps">
                <span>01 / Create database</span>
                <span>02 / Define table</span>
                <span>03 / Add records</span>
              </div>
            </div>
          ) : (
            <>
              <div className="database-heading">
                <div>
                  <span className="eyebrow">
                    {remote ? "CONNECTED · READ ONLY" : "LOCAL DATABASE"}
                  </span>
                  <h2>{selectedDb.name}</h2>
                </div>
                <div className="form-actions">
                  <button
                    disabled={busy}
                    onClick={() =>
                      manage(
                        "Rename database",
                        "patch",
                        `/databases/${databaseId}`,
                        selectedDb.name,
                      )
                    }
                  >
                    <Pencil size={15} /> Rename
                  </button>
                  <button
                    className="danger-text"
                    disabled={busy}
                    onClick={() =>
                      manage(
                        remote ? "Disconnect database" : "Delete database",
                        "delete",
                        `/databases/${databaseId}`,
                        selectedDb.name,
                        remote
                          ? {
                              disconnect: true,
                              description:
                                "Remove this saved connection and its table catalog. The remote database and its records will not be changed.",
                            }
                          : {
                              description:
                                "Permanently delete this local database and all its tables and records. Saved query history may still contain previous answer snapshots.",
                            },
                      )
                    }
                  >
                    <Trash2 size={15} />{" "}
                    {remote ? "Disconnect" : "Delete database"}
                  </button>
                  {remote && (
                    <>
                      <button
                        disabled={busy}
                        onClick={() => setConnection(selectedDb)}
                      >
                        <Link2 size={15} /> Update connection
                      </button>
                      <button
                        disabled={busy}
                        onClick={() =>
                          action(async () => {
                            await api.post(`/databases/${databaseId}/refresh`);
                            setRevision((r) => r + 1);
                            setMessage("Connected schema refreshed.");
                          })
                        }
                      >
                        <RefreshCw size={15} /> Refresh schema
                      </button>
                    </>
                  )}
                  {!remote && (
                    <button
                      disabled={
                        busy ||
                        selectedDb.status !== "READY" ||
                        tables.length >= 12
                      }
                      onClick={() => {
                        setShowTable(!showTable);
                        setShowRow(false);
                      }}
                    >
                      <Plus size={16} /> New table
                    </button>
                  )}
                </div>
              </div>
              {remote && (
                <p className="connection-status">{selectedDb.connectionInfo}</p>
              )}
              {selectedDb.status !== "READY" && (
                <p className="notice">
                  Database setup is {label(selectedDb.status).toLowerCase()}.
                  Use Retry creation if it failed.
                </p>
              )}
              <div className="table-nav" aria-label="Tables">
                {tables.map((t) => (
                  <button
                    disabled={busy}
                    key={t.id}
                    className={t.id === tableId && !showTable ? "selected" : ""}
                    onClick={() => chooseTable(t.id)}
                  >
                    <Table2 size={15} />
                    {t.name}
                  </button>
                ))}
              </div>
              {showTable ? (
                <form
                  className="stack-form table-builder"
                  onSubmit={(e) => {
                    e.preventDefault();
                    action(async () => {
                      const r = await api.post(
                        `/databases/${databaseId}/tables`,
                        { name: tableName.trim(), columns },
                      );
                      setTables((ts) => [...ts, r.data]);
                      setTableName("");
                      setColumns([blankColumn()]);
                      chooseTable(r.data.id);
                      setRevision((v) => v + 1);
                      setMessage("Table created. Add your first row.");
                    });
                  }}
                >
                  <h3>Define a table</h3>
                  <label>
                    Table name
                    <input
                      required
                      pattern="[A-Za-z][A-Za-z0-9 _\-]{0,59}"
                      maxLength={60}
                      placeholder="Students"
                      value={tableName}
                      onChange={(e) => setTableName(e.target.value)}
                    />
                  </label>
                  <p>
                    Each row gets an automatic ID. Add the fields you want to
                    store below.
                  </p>
                  {columns.map((c, index) => (
                    <div className="column-builder" key={index}>
                      <label>
                        Column {index + 1}
                        <input
                          aria-label={`Column ${index + 1} name`}
                          required
                          pattern="[a-z][a-z0-9_]{0,30}"
                          maxLength={31}
                          placeholder="student_name"
                          value={c.name}
                          onChange={(e) =>
                            setColumns((cols) =>
                              cols.map((v, i) =>
                                i === index
                                  ? { ...v, name: e.target.value }
                                  : v,
                              ),
                            )
                          }
                        />
                      </label>
                      <label>
                        Type
                        <select
                          value={c.type}
                          aria-label={`Column ${index + 1} type`}
                          onChange={(e) =>
                            setColumns((cols) =>
                              cols.map((v, i) =>
                                i === index
                                  ? { ...v, type: e.target.value }
                                  : v,
                              ),
                            )
                          }
                        >
                          {[
                            "TEXT",
                            "INTEGER",
                            "DECIMAL",
                            "BOOLEAN",
                            "DATE",
                          ].map((t) => (
                            <option key={t}>{t}</option>
                          ))}
                        </select>
                      </label>
                      <label className="checkbox-label">
                        <input
                          type="checkbox"
                          checked={c.required}
                          onChange={(e) =>
                            setColumns((cols) =>
                              cols.map((v, i) =>
                                i === index
                                  ? { ...v, required: e.target.checked }
                                  : v,
                              ),
                            )
                          }
                        />{" "}
                        Required
                      </label>
                      <button
                        type="button"
                        aria-label={`Remove column ${index + 1}`}
                        disabled={columns.length === 1}
                        onClick={() =>
                          setColumns((cols) =>
                            cols.filter((_, i) => i !== index),
                          )
                        }
                      >
                        <X size={16} />
                      </button>
                    </div>
                  ))}
                  <small>
                    Column names use lowercase letters, numbers and underscores,
                    starting with a letter.
                  </small>
                  <div className="form-actions">
                    <button
                      type="button"
                      disabled={columns.length >= 20 || busy}
                      onClick={() => setColumns((c) => [...c, blankColumn()])}
                    >
                      <Plus size={15} /> Add column
                    </button>
                    <button className="primary" disabled={busy}>
                      Create table
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => setShowTable(false)}
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              ) : table ? (
                <>
                  <div className="rows-heading">
                    <div>
                      <h3>{table.name}</h3>
                      <span>
                        {data
                          ? `${data.total} ${data.total === 1 ? "record" : "records"}`
                          : "Loading records…"}{" "}
                        · {table.columns.length} fields
                      </span>
                    </div>
                    <div className="form-actions">
                      <Link
                        className="query-table-link"
                        to={`/?table=${table.id}`}
                      >
                        Ask about this table <ArrowRight size={15} />
                      </Link>
                      {!remote && (
                        <button
                          className="primary"
                          disabled={busy || data?.total >= 1000}
                          onClick={() => openRow(null)}
                        >
                          <Plus size={16} /> Add row
                        </button>
                      )}
                    </div>
                  </div>
                  {!remote && (
                    <div className="table-management form-actions">
                      <button
                        disabled={busy}
                        onClick={() =>
                          manage("Rename table", "patch", tablePath, table.name)
                        }
                      >
                        <Pencil size={14} /> Rename table
                      </button>
                      <button
                        disabled={busy}
                        onClick={() => setShowColumns(!showColumns)}
                      >
                        <Settings2 size={14} /> Edit columns
                      </button>
                      <button
                        className="danger-text"
                        disabled={busy}
                        onClick={() =>
                          manage(
                            "Delete table",
                            "delete",
                            tablePath,
                            table.name,
                          )
                        }
                      >
                        <Trash2 size={14} /> Delete table
                      </button>
                    </div>
                  )}
                  {showColumns && !remote && (
                    <section className="column-manager">
                      <h3>Table columns</h3>
                      <p>
                        Rename or add fields without losing records. Removing a
                        field permanently deletes its values.
                      </p>
                      {table.columns.map((c) => (
                        <div key={c.name} className="column-manager-row">
                          <span>
                            <strong>{c.name}</strong>{" "}
                            <small>
                              {c.type}
                              {c.required ? " · Required" : ""}
                            </small>
                          </span>
                          <div className="form-actions">
                            <button
                              disabled={busy}
                              onClick={() =>
                                manage(
                                  "Rename column",
                                  "patch",
                                  `${tablePath}/columns/${encodeURIComponent(c.name)}`,
                                  c.name,
                                  { column: true },
                                )
                              }
                            >
                              Rename
                            </button>
                            <button
                              className="danger-text"
                              disabled={busy || table.columns.length <= 1}
                              onClick={() =>
                                manage(
                                  "Delete column",
                                  "delete",
                                  `${tablePath}/columns/${encodeURIComponent(c.name)}`,
                                  c.name,
                                )
                              }
                            >
                              Delete
                            </button>
                          </div>
                        </div>
                      ))}
                      <button
                        disabled={busy || table.columns.length >= 20}
                        onClick={() =>
                          manage(
                            "Add column",
                            "post",
                            `${tablePath}/columns`,
                            "",
                            { column: true },
                          )
                        }
                      >
                        <Plus size={15} /> Add column
                      </button>
                    </section>
                  )}
                  {data?.truncated && (
                    <p className="notice">
                      Preview shows the first 1,000 records. Ask a filtered
                      question about this table to search the source database.
                    </p>
                  )}
                  {showRow && !remote && (
                    <form onSubmit={saveRow} className="stack-form row-editor">
                      <h3>
                        {editing === null
                          ? "Add a record"
                          : `Edit record #${editing}`}
                      </h3>
                      <div className="row-fields">
                        {table.columns.map((c) => (
                          <label key={c.name}>
                            {c.name}
                            {c.required ? " *" : ""}
                            {c.type === "BOOLEAN" ? (
                              <select
                                required={c.required}
                                value={values[c.name] ?? ""}
                                onChange={(e) =>
                                  setValues((v) => ({
                                    ...v,
                                    [c.name]: e.target.value,
                                  }))
                                }
                              >
                                <option value="">No value</option>
                                <option value="true">True</option>
                                <option value="false">False</option>
                              </select>
                            ) : (
                              <input
                                required={c.required}
                                type={
                                  c.type === "DATE"
                                    ? "date"
                                    : ["INTEGER", "DECIMAL"].includes(c.type)
                                      ? "number"
                                      : "text"
                                }
                                step={c.type === "DECIMAL" ? "0.0001" : "1"}
                                maxLength={4000}
                                value={values[c.name] ?? ""}
                                onChange={(e) =>
                                  setValues((v) => ({
                                    ...v,
                                    [c.name]: e.target.value,
                                  }))
                                }
                              />
                            )}
                            <small>{label(c.type)}</small>
                          </label>
                        ))}
                      </div>
                      <div className="form-actions">
                        <button className="primary" disabled={busy}>
                          {busy ? "Saving…" : "Save record"}
                        </button>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => setShowRow(false)}
                        >
                          Cancel
                        </button>
                      </div>
                    </form>
                  )}
                  <div className="data-grid">
                    <table>
                      <thead>
                        <tr>
                          {!remote && <th>ID</th>}
                          {table.columns.map((c) => (
                            <th key={c.name}>
                              {c.name}
                              <small>{label(c.type)}</small>
                            </th>
                          ))}
                          {!remote && <th>Actions</th>}
                        </tr>
                      </thead>
                      <tbody>
                        {data?.rows.map((row, index) => (
                          <tr key={remote ? index : row._row_id}>
                            {!remote && <td>{row._row_id}</td>}
                            {table.columns.map((c) => (
                              <td key={c.name}>
                                {row[c.name] === null ? (
                                  <span className="null-value">Empty</span>
                                ) : (
                                  String(row[c.name])
                                )}
                              </td>
                            ))}
                            {!remote && (
                              <td>
                                <button
                                  aria-label={`Edit row ${row._row_id}`}
                                  disabled={busy}
                                  onClick={() => openRow(row)}
                                >
                                  <Pencil size={14} /> Edit
                                </button>
                                <button
                                  className="danger-text"
                                  disabled={busy}
                                  aria-label={`Delete row ${row._row_id}`}
                                  onClick={() =>
                                    manage(
                                      "Delete row",
                                      "delete",
                                      `${tablePath}/rows/${row._row_id}`,
                                      String(row._row_id),
                                    )
                                  }
                                >
                                  <Trash2 size={14} /> Delete
                                </button>
                              </td>
                            )}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {data?.rows.length === 0 && (
                      <p className="empty-table">
                        {remote
                          ? "No records are visible in this table."
                          : "Your table is ready. Add a row to get started."}
                      </p>
                    )}
                  </div>
                  <div className="pagination">
                    <button
                      disabled={page === 0 || busy}
                      onClick={() => setPage((p) => p - 1)}
                    >
                      Previous
                    </button>
                    <span>Page {page + 1} · 50 rows per page</span>
                    <button
                      disabled={!data || (page + 1) * 50 >= data.total || busy}
                      onClick={() => setPage((p) => p + 1)}
                    >
                      Next
                    </button>
                  </div>
                </>
              ) : (
                !showTable && (
                  <div className="database-empty">
                    <Table2 size={30} />
                    <h3>
                      {tables.length
                        ? "Select a table above"
                        : "Create your first table"}
                    </h3>
                    <p>
                      {remote
                        ? "Choose a connected table to preview its live records."
                        : "Define columns for the information you want to store."}
                    </p>
                  </div>
                )
              )}
            </>
          )}
        </section>
      </div>
      <p className="database-note">
        Local databases are stored in PostgreSQL on this installation. Connected
        databases are read live and cannot be modified here. You can create up
        to 12 tables per database, with 20 columns and 1,000 rows per table.
        Personal queries support filtering, sorting and a join between two
        related tables. Document matching remains available in the separate
        sample commerce demonstration.
      </p>
    </>
  );
}
