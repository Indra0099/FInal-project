import { useEffect, useRef, useState } from "react";
import { Loader2, X, Link2 } from "lucide-react";
import { api, errorMessage } from "./api";

export function DatabaseActionDialog({ action, onClose, onDone }) {
  const dialog = useRef(null);
  const [name, setName] = useState(action.name || "");
  const inFlight = useRef(false);
  const [type, setType] = useState("TEXT");
  const [required, setRequired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const destructive = action.method === "delete";
  const adding = action.method === "post";
  useEffect(() => {
    dialog.current?.showModal();
  }, []);
  async function submit(e) {
    e.preventDefault();
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError("");
    try {
      const body = destructive
        ? { confirmation: action.name }
        : adding
          ? { name, type, required }
          : { name };
      await api.request({
        method: action.method,
        url: action.path,
        data: body,
      });
      await onDone(action);
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  return (
    <dialog
      className="database-dialog"
      ref={dialog}
      onCancel={(e) => {
        if (busy) e.preventDefault();
        else onClose();
      }}
      aria-labelledby="database-action-title"
    >
      <form onSubmit={submit} className="stack-form">
        <div className="dialog-heading">
          <h2 id="database-action-title">{action.title}</h2>
          <button
            type="button"
            aria-label="Close dialog"
            disabled={busy}
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </div>
        <p>
          {action.description ||
            (destructive
              ? "This permanently removes the selected data. This cannot be undone."
              : adding
                ? "New optional fields are empty for existing records. Required fields can only be added to an empty table."
                : "Existing records are preserved. Future questions will use the new name.")}
        </p>
        {error && (
          <p className="notice" role="alert">
            {error}
          </p>
        )}
        {destructive ? (
          <div className="delete-target" role="note">
            <span>
              {action.disconnect ? "Connection to disconnect" : "Selected item"}
            </span>
            <strong>{action.name}</strong>
            <small>
              {action.disconnect
                ? "Confirm below to remove this saved connection. Remote data stays unchanged."
                : "Confirm below to permanently delete this item. Cancel keeps it unchanged."}
            </small>
          </div>
        ) : (
          <label>
            {adding ? "Column name" : "New name"}
            <input
              autoFocus
              required
              value={name}
              maxLength={action.column ? 31 : 60}
              pattern={
                action.column
                  ? "[a-z][a-z0-9_]{0,30}"
                  : "[A-Za-z][A-Za-z0-9 _\\-]{0,59}"
              }
              onChange={(e) => setName(e.target.value)}
            />
          </label>
        )}
        {adding && (
          <>
            <label>
              Data type
              <select value={type} onChange={(e) => setType(e.target.value)}>
                {["TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE"].map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </label>
            <label className="checkbox-field">
              <input
                type="checkbox"
                checked={required}
                onChange={(e) => setRequired(e.target.checked)}
              />{" "}
              Required (empty tables only)
            </label>
          </>
        )}
        <div className="form-actions">
          <button
            type="submit"
            className={destructive ? "danger-button" : "primary"}
            disabled={busy}
          >
            {busy
              ? "Applying…"
              : destructive
                ? action.disconnect
                  ? "Disconnect"
                  : "Delete permanently"
                : "Save changes"}
          </button>
          <button type="button" disabled={busy} onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </dialog>
  );
}

export function ConnectionForm({ database, onDone, onCancel }) {
  const [name, setName] = useState(database?.name || "");
  const [schema, setSchema] = useState(database?.schema || "public");
  const [link, setLink] = useState("");
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const body = {
        name: name.trim(),
        connectionUrl: link.trim(),
        schema: schema.trim(),
      };
      const r = database
        ? await api.put(`/databases/${database.id}/connection`, body, {
            timeout: 30000,
          })
        : await api.post("/databases/connect", body, { timeout: 30000 });
      setLink("");
      await onDone(r.data);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="external-connection-form">
      <div className="dialog-heading">
        <div>
          <span className="eyebrow">LIVE POSTGRESQL DATA</span>
          <h2>
            <Link2 size={20} />{" "}
            {database ? "Update connection" : "Connect a database"}
          </h2>
        </div>
        <button
          aria-label="Close connection form"
          onClick={onCancel}
          disabled={busy}
        >
          <X size={18} />
        </button>
      </div>
      <p>
        Use a PostgreSQL connection link with a SELECT-only database user. We
        discover the tables and read live records when you ask a question.
        Connected tables stay read-only.
      </p>
      <form onSubmit={submit} className="stack-form">
        {error && (
          <p className="notice" role="alert">
            {error}
          </p>
        )}
        <div className="row-fields">
          <label>
            Connection name
            <input
              required
              value={name}
              maxLength={60}
              pattern="[A-Za-z][A-Za-z0-9 _\-]{0,59}"
              placeholder="e.g. College server"
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label>
            Schema
            <input
              required
              value={schema}
              maxLength={63}
              pattern="[A-Za-z_][A-Za-z0-9_]{0,62}"
              onChange={(e) => setSchema(e.target.value)}
            />
          </label>
        </div>
        <label>
          PostgreSQL connection link
          <input
            type="password"
            autoComplete="off"
            required
            maxLength={2048}
            value={link}
            placeholder="postgresql://user:password@host:5432/database?sslmode=verify-full"
            onChange={(e) => setLink(e.target.value)}
          />
        </label>
        <small>
          The link is encrypted on this installation and never sent to Gemini.
          Matching records are sent to Gemini to answer your question. Enable
          the server hostname in EXTERNAL_DB_ALLOWED_HOSTS in .env before
          connecting. For a database on this computer, use host.docker.internal
          from Docker. See the connection setup guide in
          docs/13-DATABASE-MANAGEMENT-AND-CONNECTIONS.md.
        </small>
        <div className="form-actions">
          <button className="primary" disabled={busy}>
            {busy ? (
              <Loader2 size={16} className="spin" />
            ) : (
              <Link2 size={16} />
            )}{" "}
            {busy ? "Checking connection…" : "Connect and discover tables"}
          </button>
          <button type="button" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
        </div>
      </form>
    </section>
  );
}
