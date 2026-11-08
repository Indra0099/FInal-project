import { useEffect, useRef, useState } from "react";
import {
  NavLink,
  Route,
  Routes,
  useNavigate,
  useSearchParams,
  Link,
} from "react-router-dom";
import {
  ArrowUpRight,
  ArrowRight,
  Layers3,
  MessageSquare,
  Files,
  History,
  BookOpen,
  Plus,
  Database,
  FileText,
  Link2,
  Check,
  CircleAlert,
  Loader2,
  Download,
  RefreshCw,
  ChevronDown,
  Search,
  ShieldCheck,
  SlidersHorizontal,
} from "lucide-react";
import { api, errorMessage, label } from "./api";
import { AccountMenu } from "./Auth";
import DatabaseStudio from "./DatabaseStudio";

function useLoad(path, interval = 0) {
  const [data, setData] = useState(null),
    [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    const load = () =>
      api
        .get(path)
        .then((r) => {
          if (active) {
            setData(r.data);
            setError("");
          }
        })
        .catch((e) => {
          if (active) setError(errorMessage(e));
        });
    load();
    const timer = interval ? setInterval(load, interval) : null;
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [path, interval]);
  return { data, error };
}
function Notice({ children }) {
  return (
    <div className="notice" role="status">
      <CircleAlert size={17} />
      <div>{children}</div>
    </div>
  );
}
function Badge({ status }) {
  return (
    <span
      className={`badge ${["COMPLETE", "READY", "UP"].includes(status) ? "good" : ["FAILED", "DOWN"].includes(status) ? "bad" : "neutral"}`}
    >
      {["RUNNING", "PROCESSING", "QUEUED"].includes(status) && (
        <Loader2 size={12} className="spin" />
      )}
      {label(status)}
    </span>
  );
}
function App() {
  const health = useLoad("/health", 15000);
  const workspace = useRef();

  return (
    <div className="app">
      <a
        className="skip-link"
        href="#workspace"
        onClick={(event) => {
          event.preventDefault();
          workspace.current?.focus();
          workspace.current?.scrollIntoView({ block: "start" });
        }}
      >
        Skip to workspace
      </a>
      <header className="app-header">
        <Link to="/" className="brand" aria-label="ShardQuery AI home">
          <span className="brand-symbol" aria-hidden="true">
            <i />
            <i />
            <i />
            <i />
          </span>
          <span>
            ShardQuery<span className="brand-ai">AI</span>
          </span>
        </Link>
        <nav className="main-nav" aria-label="Main navigation">
          {[
            [Search, "/", "Query workspace"],
            [Database, "/databases", "My databases"],
            [Files, "/documents", "Document library"],
            [History, "/history", "History"],
            [BookOpen, "/guide", "How it works"],
          ].map(([Icon, to, text]) => (
            <NavLink key={to} end={to === "/"} to={to}>
              <Icon size={16} />
              {text}
            </NavLink>
          ))}
        </nav>
        <AccountMenu />
      </header>
      <main id="workspace" ref={workspace} tabIndex={-1}>
        <div className="page">
          <Routes>
            <Route path="/" element={<Ask health={health} />} />
            <Route path="/databases" element={<DatabaseStudio />} />
            <Route path="/documents" element={<Documents health={health} />} />
            <Route path="/history" element={<QueryHistory />} />
            <Route path="/guide" element={<Guide health={health} />} />
            <Route
              path="*"
              element={
                <div className="empty">
                  <h1>Page not found</h1>
                  <Link to="/">Return to your workspace</Link>
                </div>
              }
            />
          </Routes>
        </div>
      </main>
      <footer>
        <span>
          <span className="footer-mark">SQ</span> SHARDQUERY AI{" "}
          <span className="footer-divider">/</span> A connected view of your
          data.
        </span>
        <span>
          Local demonstration <span className="footer-divider">·</span>{" "}
          Read-only queries
        </span>
      </footer>
    </div>
  );
}

const examples = [
  {
    icon: Database,
    tag: "DATABASE QUESTION",
    text: "Which laptops cost more than ₹50,000?",
    question: "List laptops priced above ₹50,000.",
    mode: "SQL",
  },
  {
    icon: FileText,
    tag: "DOCUMENT QUESTION",
    text: "What does our return policy cover?",
    question: "What does the uploaded return policy say about laptop returns?",
    mode: "RAG",
  },
  {
    icon: Link2,
    tag: "COMBINED QUESTION",
    text: "Who bought a laptop, and what did they think?",
    question:
      "Find customers who purchased laptops above ₹50,000 and summarize their uploaded product reviews.",
    mode: "HYBRID",
  },
];
function Ask({ health }) {
  const [question, setQuestion] = useState(""),
    [mode, setMode] = useState("AUTO"),
    [error, setError] = useState(""),
    [submitting, setSubmitting] = useState(false),
    [job, setJob] = useState(null),
    [tab, setTab] = useState("answer");
  const [params, setParams] = useSearchParams();
  const jobId = params.get("job");
  const personalTables = useLoad("/databases/tables");
  const [tableId, setTableId] = useState(params.get("table") || "");
  const [source, setSource] = useState(
    params.get("source") || (params.get("table") ? "PERSONAL" : "AUTO"),
  );
  useEffect(() => {
    if (tableId) setMode("SQL");
  }, [tableId]);
  const textarea = useRef();
  useEffect(() => {
    setError("");
    setJob(null);
    if (!jobId) return;
    let active = true,
      timer;
    const poll = async () => {
      try {
        const r = await api.get("/queries/" + jobId);
        if (!active) return;
        setError("");
        setJob(r.data);
        if (r.data.result && !params.has("source") && !params.has("table")) {
          setSource(
            r.data.result.requestSource ||
              (r.data.result.tableId ? "PERSONAL" : "AUTO"),
          );
          setTableId(
            r.data.result.requestTableId ||
              (!r.data.result.requestSource ? r.data.result.tableId : "") ||
              "",
          );
        }
        if (["RUNNING", "QUEUED"].includes(r.data.status))
          timer = setTimeout(poll, 1200);
      } catch (e) {
        if (active) {
          setError(errorMessage(e));
          if (
            !e.response ||
            e.response.status >= 500 ||
            e.response.status === 429
          )
            timer = setTimeout(poll, 3000);
        }
      }
    };
    poll();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [jobId]);
  const busy = submitting || ["QUEUED", "RUNNING"].includes(job?.status);
  async function submit(body) {
    setError("");
    setSubmitting(true);
    setTab("answer");
    try {
      const { data } = await api.post("/queries", body);
      setParams({
        job: data.id,
        source: body.source || "AUTO",
        ...(body.tableId ? { table: body.tableId } : {}),
      });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }
  function useExample(e) {
    setTableId(e.tableId || "");
    setSource(e.source || "DEMO");
    setQuestion(e.question);
    setMode(e.mode);
    textarea.current?.focus();
  }
  const result = job?.result;
  return (
    <>
      <PageTitle
        eyebrow="DATABASE + DOCUMENT INTELLIGENCE"
        title="Query workspace"
        description="One question. Answers from your records and documents, with the evidence attached."
      />
      <div className="query-layout">
        <div className="query-main">
          <form
            className="question-card"
            onSubmit={(e) => {
              e.preventDefault();
              submit({
                question,
                mode: tableId ? "SQL" : mode,
                tableId: tableId || null,
                source,
              });
            }}
          >
            <label className="data-source-selector">
              Query data source
              <select
                value={tableId || source}
                disabled={busy}
                onChange={(e) => {
                  const value = e.target.value;
                  const isTable = !["AUTO", "PERSONAL", "DEMO"].includes(value);
                  const nextSource = isTable ? "PERSONAL" : value;
                  setTableId(isTable ? value : "");
                  setSource(nextSource);
                  setMode(nextSource === "PERSONAL" ? "SQL" : "AUTO");
                  setParams({
                    source: nextSource,
                    ...(isTable ? { table: value } : {}),
                  });
                }}
              >
                <option value="AUTO">
                  Automatically choose from my data and sample databases
                </option>
                <option value="PERSONAL">My databases — all tables</option>
                <option value="DEMO">
                  Sample commerce databases + my documents
                </option>
                {personalTables.data?.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.databaseName} / {t.name}
                  </option>
                ))}
              </select>
              <small>
                {tableId
                  ? "Only the selected personal table will be queried."
                  : source === "PERSONAL"
                    ? "Use your own tables, including linked records across two databases."
                    : source === "AUTO"
                      ? "Matches your question to your saved tables or the sample data automatically."
                      : "Shared sample commerce data; documents are private to your account."}
              </small>
            </label>
            <div className="composer-heading">
              <span>
                <span className="step-number">01</span> Write your question
              </span>
              <span className="editor-label">NATURAL LANGUAGE</span>
            </div>
            <div className="editor-body">
              <span className="editor-gutter" aria-hidden="true">
                01
                <br />
                02
                <br />
                03
              </span>
              <label className="sr-only" htmlFor="question">
                Ask a question about your data
              </label>
              <textarea
                id="question"
                ref={textarea}
                value={question}
                minLength={3}
                maxLength={2000}
                required
                onChange={(e) => setQuestion(e.target.value)}
                placeholder={
                  tableId
                    ? "Show records where marks are above 80, sorted by marks…"
                    : source !== "DEMO" && personalTables.data?.length
                      ? "Ask about your saved data, for example students whose fees are above ₹150000…"
                      : "Find customers who purchased laptops above ₹50,000 and summarize their reviews…"
                }
                rows={4}
              />
            </div>
            <div className="question-toolbar">
              <div className="mode-picker" aria-label="Query mode">
                {[
                  ["AUTO", "Auto"],
                  ["SQL", "Database"],
                  ["RAG", "Documents"],
                  ["HYBRID", "Combined"],
                ].map(([id, name]) => (
                  <button
                    type="button"
                    key={id}
                    className={id === mode ? "selected" : ""}
                    aria-pressed={id === mode}
                    disabled={
                      busy ||
                      ((Boolean(tableId) || source === "PERSONAL") &&
                        !["SQL", "AUTO"].includes(id))
                    }
                    onClick={() => setMode(id)}
                  >
                    {name}
                  </button>
                ))}
              </div>
              <button
                className="primary ask-button"
                type="submit"
                aria-label={busy ? "Running query" : "Run query"}
                disabled={busy || !question.trim()}
              >
                {busy ? (
                  <Loader2 size={17} className="spin" />
                ) : (
                  <ArrowRight size={17} />
                )}
                <span>{busy ? "Running…" : "Run query"}</span>
              </button>
            </div>
          </form>
          <div className="under-composer">
            <ShieldCheck size={13} />
            <span>
              Read-only database access · Sources included · No data changes
            </span>
          </div>
          {health.error && <Notice>{health.error}</Notice>}
          {health.data && !health.data.aiConfigured && (
            <div className="setup-banner">
              <span>
                <span className="dot amber" />
                AI is not connected yet. You can explore real database records
                below.
              </span>
              <Link to="/guide">
                Connect free-tier Gemini <ArrowUpRight size={14} />
              </Link>
            </div>
          )}
          {error && <Notice>{error}</Notice>}
          {!job && (
            <section className="result-placeholder">
              <div className="placeholder-icon">
                <Layers3 size={27} />
              </div>
              <div>
                <span className="eyebrow">QUERY OUTPUT</span>
                <h2>Follow the question. Find the evidence.</h2>
                <p>
                  Run a query to see your answer, matching records and original
                  sources here.
                </p>
              </div>
              <div className="output-types">
                <span>01 / Answer</span>
                <span>02 / Records</span>
                <span>03 / Sources</span>
              </div>
            </section>
          )}
          {!tableId && source !== "PERSONAL" && (
            <SqlExplorer busy={busy} submit={submit} />
          )}
          {job && (
            <section className="result-panel" aria-live="polite">
              <div className="result-heading">
                <div>
                  <div className="eyebrow">QUERY OUTPUT</div>
                  <h2>{job.question}</h2>
                </div>
                <Badge status={job.status} />
              </div>
              {busy && (
                <div className="working">
                  <Loader2 className="spin" />
                  <div>
                    <strong>Following the evidence</strong>
                    <p>
                      Planning the query, retrieving records and checking
                      relevant documents. You can revisit this query in History.
                    </p>
                  </div>
                </div>
              )}
              {job.error && <Notice>{job.error}</Notice>}
              {result && (
                <>
                  {result.tables?.length > 0 && (
                    <div className="query-data-used">
                      <strong>Data used</strong>
                      {result.tables.map((t) => (
                        <span key={t.id}>
                          {t.databaseName} / {t.name}
                        </span>
                      ))}
                    </div>
                  )}
                  <div className="result-meta">
                    <span>{label(result.mode)} query</span>
                    <span>{(result.durationMs / 1000).toFixed(2)}s</span>
                    <span>
                      {result.cached
                        ? "Cached result · " +
                          new Date(result.generatedAt).toLocaleTimeString()
                        : "Retrieved " +
                          new Date(result.generatedAt).toLocaleTimeString()}
                    </span>
                  </div>
                  {result.warnings.map((w, i) => (
                    <Notice key={i}>{w}</Notice>
                  ))}
                  <div className="tabs" role="tablist">
                    {[
                      ["answer", "Answer"],
                      ["records", `Records (${result.rows.length})`],
                      ["sources", `Sources (${result.sources.length})`],
                    ].map(([id, title]) => (
                      <button
                        key={id}
                        role="tab"
                        aria-selected={tab === id}
                        onClick={() => setTab(id)}
                      >
                        {title}
                      </button>
                    ))}
                  </div>
                  <div className="tab-content" role="tabpanel">
                    {tab === "answer" && (
                      <>
                        <div className="answer-text">{result.answer}</div>
                        <div className="answer-foot">
                          <span>
                            {["OPENAI", "GEMINI"].includes(result.answerMethod)
                              ? "AI summary · verify against the evidence"
                              : result.answerMethod === "DETERMINISTIC_SQL"
                                ? "Direct database result · no AI generated text"
                                : "Evidence status"}
                          </span>
                          <button
                            className="text-button"
                            onClick={() => downloadReport(job)}
                          >
                            <Download size={14} />
                            Save result
                          </button>
                        </div>
                        {result.sources.length > 0 && (
                          <div className="source-chips">
                            {result.sources.map((s) => (
                              <button
                                key={s.id}
                                onClick={() => setTab("sources")}
                              >
                                <FileText size={13} />[{s.id}] {s.filename}
                              </button>
                            ))}
                          </div>
                        )}
                      </>
                    )}
                    {tab === "records" && (
                      <Records
                        rows={result.rows}
                        demo={result.plan?.intent !== "CUSTOM_TABLE"}
                      />
                    )}
                    {tab === "sources" && <Sources sources={result.sources} />}
                  </div>
                  <details className="execution">
                    <summary>
                      <span>
                        <SlidersHorizontal size={15} />
                        How this query ran
                      </span>
                      <ChevronDown size={16} />
                    </summary>
                    <p>{result.plan?.explanation}</p>
                    {result.traces.map((t, i) => (
                      <div className="trace" key={i}>
                        <div>
                          <strong>{label(t.shard)} database</strong>
                          <Badge
                            status={
                              t.status === "COMPLETE" ? "COMPLETE" : "DOWN"
                            }
                          />
                          <span>
                            {t.rows} fetched · {t.durationMs}ms
                            {t.bucket != null ? ` · bucket ${t.bucket}` : ""}
                          </span>
                        </div>
                        <pre>{t.sql}</pre>
                        <small>Bound parameters</small>
                        <pre>{JSON.stringify(t.parameters, null, 2)}</pre>
                      </div>
                    ))}
                    {!result.traces.length && (
                      <p>
                        This was a document-only query; no SQL was executed.
                      </p>
                    )}
                    <p className="muted">
                      Domain databases are associated in the application. These
                      are independent reads, not a distributed transaction.
                    </p>
                  </details>
                </>
              )}
            </section>
          )}
        </div>
        <QueryContext
          health={health}
          onExample={useExample}
          table={personalTables.data?.find((t) => t.id === tableId)}
          tables={personalTables.data || []}
          source={source}
        />
      </div>
    </>
  );
}

function QueryContext({ health, onExample, table, tables, source }) {
  const documents = useLoad("/documents", 10000);
  const ready = documents.data?.filter((d) => d.status === "READY").length;
  if (table)
    return (
      <aside className="context-rail">
        <section className="connection-panel">
          <span className="eyebrow">YOUR SELECTED TABLE</span>
          <h2>{table.name}</h2>
          <p>{table.databaseName}</p>
          <div className="personal-columns">
            {table.columns.map((c) => (
              <div key={c.name}>
                <strong>{c.name}</strong>
                <span>
                  {label(c.type)}
                  {c.required ? " · required" : ""}
                </span>
              </div>
            ))}
          </div>
          <p className="personal-hint">
            Ask to list, filter or sort these fields. Only this table is
            queried. Choose My databases to combine related records from two
            tables. Totals and document matching are not supported here.
          </p>
          <Link className="library-link" to="/databases">
            Manage your records <ArrowUpRight size={15} />
          </Link>
        </section>
      </aside>
    );

  if (source !== "DEMO" && tables.length)
    return (
      <aside className="context-rail">
        <section className="connection-panel">
          <span className="eyebrow">YOUR PERSONAL DATABASES</span>
          <h2>
            Your data.
            <br />
            <span>Ready for questions.</span>
          </h2>
          <p className="personal-hint">
            {new Set(tables.map((t) => t.databaseId)).size} databases ·{" "}
            {tables.length} tables. Relevant tables are selected automatically;
            related records can be joined across two tables.
          </p>
          <div className="personal-columns">
            {tables.map((t) => (
              <div key={t.id}>
                <strong>
                  {t.databaseName} / {t.name}
                </strong>
                <span>{t.columns.map((c) => c.name).join(", ")}</span>
              </div>
            ))}
          </div>
          <Link className="library-link" to="/databases">
            Manage my databases <ArrowUpRight size={15} />
          </Link>
        </section>
        <section className="query-recipes">
          <h2>Ask about your data</h2>
          {tables.slice(0, 4).map((t) => (
            <button
              key={t.id}
              className="recipe"
              onClick={() =>
                onExample({
                  question: `List records from the ${t.name} table in ${t.databaseName}.`,
                  source: "PERSONAL",
                  mode: "SQL",
                })
              }
            >
              <Database size={16} />
              <span>
                <small>{t.databaseName}</small>
                <strong>Explore {t.name}</strong>
              </span>
              <ArrowUpRight size={15} />
            </button>
          ))}
        </section>
        <p className="rail-note">
          Use the source selector to explore the sample commerce databases.
        </p>
      </aside>
    );

  return (
    <aside
      className="context-rail"
      aria-label="Data connections and example queries"
    >
      <section className="connection-panel">
        <div className="rail-heading">
          <span className="eyebrow">YOUR DATA SOURCES</span>
          <Layers3 size={19} />
        </div>
        <h2>
          Separate sources.
          <br />
          <span>Connected answers.</span>
        </h2>
        <div className="connection-map">
          {[
            ["customer", "Customers"],
            ["order", "Orders"],
            ["product", "Products"],
          ].map(([key, name]) => (
            <div key={key} className="database-node">
              <Database size={17} />
              <span>{name}</span>
              <span
                className={`dot ${health.data?.shards?.[key] === "UP" ? "live" : ""}`}
                aria-label={`${name}: ${health.data?.shards?.[key] || "Checking"}`}
              />
            </div>
          ))}
        </div>
        <div className="connection-bridge" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <div className="unified-node">
          <Link2 size={17} />
          <span>One query workspace</span>
        </div>
        <Link className="library-link" to="/documents">
          <FileText size={16} />
          <span>
            {ready == null
              ? "Open document library"
              : `${ready} indexed document${ready === 1 ? "" : "s"}`}
          </span>
          <ArrowUpRight size={15} />
        </Link>
      </section>
      <section className="query-recipes">
        <div className="rail-heading">
          <h2>Start with a question</h2>
          <span>03</span>
        </div>
        <p>Choose an example to fill the editor.</p>
        {examples.map((e, index) => (
          <button key={e.mode} className="recipe" onClick={() => onExample(e)}>
            <span className="recipe-number">0{index + 1}</span>
            <span>
              <small>{e.tag}</small>
              <strong>{e.text}</strong>
            </span>
            <ArrowUpRight size={16} />
          </button>
        ))}
      </section>
      <div className="rail-note">
        <ShieldCheck size={19} />
        <p>
          <strong>Your records stay unchanged.</strong> Queries only read data.
          Each answer keeps its evidence within reach.
        </p>
      </div>
    </aside>
  );
}

function SqlExplorer({ busy, submit }) {
  const [intent, setIntent] = useState("PURCHASES"),
    [category, setCategory] = useState("laptop"),
    [price, setPrice] = useState("50000"),
    [customer, setCustomer] = useState("");
  return (
    <details className="explorer">
      <summary>
        <span>
          <Database size={16} />
          Explore the sample database
        </span>
        <span>
          No AI key needed <ChevronDown size={15} />
        </span>
      </summary>
      <p>
        Use filters to run a real, read-only query over the fictional demo
        records. This guided mode does not use AI.
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const plan = {
            mode: "SQL",
            intent,
            category: ["CUSTOMERS"].includes(intent) ? null : category || null,
            minPrice:
              ["PURCHASES", "PRODUCTS", "INVENTORY"].includes(intent) && price
                ? Number(price)
                : null,
            customerId:
              ["PURCHASES", "CUSTOMERS", "REVIEWS"].includes(intent) && customer
                ? Number(customer)
                : null,
            productId: null,
            limit: 50,
            explanation:
              "Approved SQL template selected using explicit filters.",
          };
          submit({
            source: "DEMO",
            question: `Explore ${label(intent).toLowerCase()}${plan.category ? " in " + plan.category : ""}${plan.minPrice ? " above ₹" + plan.minPrice : ""}${plan.customerId ? " for customer " + plan.customerId : ""}`,
            mode: "SQL",
            plan,
          });
        }}
      >
        <div className="form-grid">
          <label>
            Show
            <select value={intent} onChange={(e) => setIntent(e.target.value)}>
              {[
                "PURCHASES",
                "CUSTOMERS",
                "PRODUCTS",
                "REVIEWS",
                "INVENTORY",
              ].map((v) => (
                <option key={v}>{v}</option>
              ))}
            </select>
          </label>
          <label>
            Category
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              disabled={intent === "CUSTOMERS"}
            >
              <option value="">All categories</option>
              <option value="laptop">Laptops</option>
              <option value="accessory">Accessories</option>
              <option value="monitor">Monitors</option>
            </select>
          </label>
          <label>
            Price above (₹)
            <input
              type="number"
              min="0"
              max="1000000000"
              value={price}
              disabled={["CUSTOMERS", "REVIEWS"].includes(intent)}
              onChange={(e) => setPrice(e.target.value)}
            />
          </label>
          <label>
            Customer ID <small>optional</small>
            <input
              type="number"
              min="1"
              max="2147483647"
              placeholder="e.g. 101"
              value={customer}
              disabled={["PRODUCTS", "INVENTORY"].includes(intent)}
              onChange={(e) => setCustomer(e.target.value)}
            />
          </label>
        </div>
        <button className="secondary" disabled={busy}>
          Run database query <ArrowRight size={15} />
        </button>
      </form>
    </details>
  );
}
function Records({ rows, demo = true }) {
  if (!rows.length)
    return (
      <div className="empty">
        <Database />
        <h3>No database records</h3>
        <p>
          Try different filters, or inspect the document sources for a
          document-only query.
        </p>
      </div>
    );
  const keys = [...new Set(rows.flatMap(Object.keys))];
  const preferred = [
    "customer_name",
    "product_name",
    "name",
    "unit_price",
    "price",
    "quantity",
    "rating",
    "review_text",
    "available",
    "city",
    "status",
    "order_id",
  ];
  const columns = [
    ...preferred.filter((k) => keys.includes(k)),
    ...keys.filter((k) => !preferred.includes(k)),
  ];
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            {columns.map((k) => (
              <th key={k}>{label(k)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {columns.map((k) => (
                <td key={k}>
                  {r[k] == null ? (
                    <span className="muted">Unavailable</span>
                  ) : demo &&
                    ["price", "unit_price", "total_amount"].includes(k) ? (
                    new Intl.NumberFormat("en-IN", {
                      style: "currency",
                      currency: "INR",
                      maximumFractionDigits: 2,
                    }).format(Number(r[k]))
                  ) : k === "ordered_at" ? (
                    new Date(r[k]).toLocaleDateString()
                  ) : typeof r[k] === "object" ? (
                    JSON.stringify(r[k])
                  ) : (
                    String(r[k])
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
function Sources({ sources }) {
  return sources.length ? (
    <div className="sources">
      {sources.map((s) => (
        <article key={s.id}>
          <div className="source-header">
            <span className="source-id">{s.id}</span>
            <strong>{s.filename}</strong>
            <a
              href={`/api/documents/${s.documentId}/download`}
              aria-label={`Download ${s.filename}`}
            >
              <Download size={16} />
            </a>
          </div>
          <p>{s.text}</p>
          <small>
            Excerpt {s.chunk}
            {s.productId ? " · Product " + s.productId : ""}
            {s.customerId ? " · Customer " + s.customerId : ""} · Similarity{" "}
            {s.score.toFixed(3)} (not factual confidence)
          </small>
        </article>
      ))}
    </div>
  ) : (
    <div className="empty">
      <FileText />
      <h3>No document sources</h3>
      <p>
        Database-only queries do not need documents. For document questions,
        upload a file and wait for it to be indexed.
      </p>
    </div>
  );
}
function downloadReport(job) {
  const r = job.result;
  const text = `# ${job.question}\n\n${r.answer}\n\n## Warnings\n${r.warnings.map((w) => "- " + w).join("\n") || "None"}\n\n## Database records\n\n\`\`\`json\n${JSON.stringify(r.rows, null, 2)}\n\`\`\`\n\n## Sources\n${r.sources.map((s) => `\n### [${s.id}] ${s.filename}, excerpt ${s.chunk}\n${s.text}\n`).join("")}\n\nGenerated: ${r.generatedAt}\nAnswer method: ${r.answerMethod}\n`;
  const url = URL.createObjectURL(new Blob([text], { type: "text/markdown" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = "shardquery-result.md";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function Documents({ health }) {
  const [docs, setDocs] = useState([]),
    [error, setError] = useState(""),
    [loadError, setLoadError] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [file, setFile] = useState(null),
    [product, setProduct] = useState(""),
    [customer, setCustomer] = useState("");
  const input = useRef();
  const load = () =>
    api
      .get("/documents")
      .then((r) => {
        setDocs(r.data);
        setLoadError("");
      })
      .catch((e) => setLoadError(errorMessage(e)));
  useEffect(() => {
    load();
    const timer = setInterval(load, 4000);
    return () => clearInterval(timer);
  }, []);
  async function upload(e) {
    e.preventDefault();
    if (!file) return;
    setError("");
    setMessage("");
    if (file.size > 10 * 1024 * 1024) {
      setError("Choose a file up to 10 MB.");
      return;
    }
    setBusy(true);
    try {
      const form = new FormData();
      form.append("file", file);
      if (product) form.append("productId", product);
      if (customer) form.append("customerId", customer);
      const r = await api.post("/documents", form);
      setMessage(
        r.data.status === "WAITING_FOR_KEY"
          ? "File saved. Add the API key, restart, then choose Retry indexing."
          : "File saved. Indexing will start shortly.",
      );
      setFile(null);
      input.current.value = "";
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function retry(id) {
    try {
      setError("");
      await api.post(`/documents/${id}/retry`);
      await load();
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  return (
    <>
      <PageTitle
        eyebrow="DOCUMENT INTELLIGENCE"
        title="Document library"
        description="Upload reviews, policies and product notes. Every answer can lead back to its source."
      />
      <div className="library-layout">
        <form className="upload-card" onSubmit={upload}>
          <div className="upload-intro">
            <div className="file-icon">
              <Files size={25} />
            </div>
            <div>
              <h2>Add a document</h2>
              <p>PDF, Word (.docx) or UTF-8 text · Up to 10 MB</p>
            </div>
          </div>
          <label className="file-drop">
            <input
              ref={input}
              type="file"
              accept=".pdf,.docx,.txt"
              required
              onChange={(e) => setFile(e.target.files?.[0] || null)}
            />
            <Plus size={22} />
            <strong>{file ? file.name : "Choose a file to upload"}</strong>
            <span>Readable text only. Scanned PDFs need OCR first.</span>
          </label>
          <details className="association" open>
            <summary>
              Connect this document to a record <span>optional</span>
            </summary>
            <p>
              For a customer review, add both IDs. Combined queries use these
              links to retrieve the right customer's review of the right
              product.
            </p>
            <div className="form-grid two">
              <label>
                Product ID
                <input
                  type="number"
                  min="1"
                  value={product}
                  placeholder="201"
                  onChange={(e) => setProduct(e.target.value)}
                />
              </label>
              <label>
                Customer ID
                <input
                  type="number"
                  min="1"
                  max="2147483647"
                  value={customer}
                  placeholder="101"
                  onChange={(e) => setCustomer(e.target.value)}
                />
              </label>
            </div>
          </details>
          <div className="upload-bottom">
            <small>
              Document text is sent to Google Gemini when indexing is enabled.
            </small>
            <button className="primary" disabled={busy || !file}>
              {busy ? (
                <Loader2 size={16} className="spin" />
              ) : (
                <Plus size={16} />
              )}
              Add document
            </button>
          </div>
        </form>
        <section className="library-content" aria-label="Uploaded documents">
          {error && <Notice>{error}</Notice>}
          {loadError && <Notice>{loadError}</Notice>}
          {message && (
            <div className="success" role="status">
              <Check size={16} />
              {message}
            </div>
          )}
          <div className="section-title">
            <h2>
              Your documents <span className="count">{docs.length}</span>
            </h2>
            <button className="text-button" onClick={load}>
              <RefreshCw size={14} />
              Refresh
            </button>
          </div>
          {docs.length ? (
            <div className="document-list">
              {docs.map((d) => (
                <article key={d.id}>
                  <FileText className="document-icon" />
                  <div className="document-info">
                    <h3>{d.name}</h3>
                    <p>
                      {new Date(d.createdAt).toLocaleDateString()} · {d.chunks}{" "}
                      excerpts{d.productId ? " · Product " + d.productId : ""}
                      {d.customerId ? " · Customer " + d.customerId : ""}
                    </p>
                    {d.error && (
                      <small className="error-text">
                        {d.error} · Attempt {d.attempts}/3
                      </small>
                    )}
                  </div>
                  <div className="document-actions">
                    <Badge status={d.status} />
                    {["FAILED", "WAITING_FOR_KEY"].includes(d.status) && (
                      <button
                        className="text-button"
                        onClick={() => retry(d.id)}
                      >
                        Retry indexing
                      </button>
                    )}
                    <a
                      href={`/api/documents/${d.id}/download`}
                      aria-label={`Download ${d.name}`}
                    >
                      <Download size={17} />
                    </a>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="empty empty-border">
              <Files size={30} />
              <h3>Your library starts here</h3>
              <p>
                Add a document above. Sample policies and customer reviews are
                included in the project's samples folder.
              </p>
            </div>
          )}
        </section>
      </div>
    </>
  );
}
function PageTitle({ eyebrow, title, description }) {
  return (
    <section className="page-title">
      <div className="eyebrow">{eyebrow}</div>
      <h1>{title}</h1>
      <p>{description}</p>
    </section>
  );
}
function QueryHistory() {
  const { data, error } = useLoad("/queries", 5000),
    navigate = useNavigate();
  return (
    <>
      <PageTitle
        eyebrow="SAVED EXECUTIONS"
        title="Query history"
        description="Revisit answers, inspect evidence and follow the progress of recent queries."
      />
      {error && <Notice>{error}</Notice>}
      {data?.length ? (
        <div className="history-list">
          {data.map((j) => (
            <button key={j.id} onClick={() => navigate("/?job=" + j.id)}>
              <div className="history-icon">
                <MessageSquare size={20} />
              </div>
              <div>
                <h3>{j.question}</h3>
                <p>
                  {new Date(j.createdAt).toLocaleString()} · {label(j.mode)}
                </p>
              </div>
              <Badge status={j.status} />
              <ArrowUpRight size={18} />
            </button>
          ))}
        </div>
      ) : (
        <div className="empty empty-border">
          <History size={28} />
          <h3>No queries yet</h3>
          <p>Your latest 50 queries will appear here.</p>
          <Link className="secondary" to="/">
            Ask your first question <ArrowRight size={15} />
          </Link>
        </div>
      )}
    </>
  );
}
function Guide({ health }) {
  return (
    <>
      <PageTitle
        eyebrow="THE SHARDQUERY WORKFLOW"
        title="Inside the engine"
        description="A clear path from your question to the records and documents that support the answer."
      />
      <div className="workflow-strip">
        {[
          [MessageSquare, "01", "Ask", "Write a question in plain English."],
          [Database, "02", "Retrieve", "Read the relevant database records."],
          [FileText, "03", "Connect", "Find related document excerpts."],
          [Check, "04", "Explain", "Read the answer and inspect its sources."],
        ].map(([Icon, n, t, d]) => (
          <div key={n}>
            <div>
              <Icon size={22} />
              <small>{n}</small>
            </div>
            <h3>{t}</h3>
            <p>{d}</p>
          </div>
        ))}
      </div>
      <div className="guide-grid">
        <article className="guide-card">
          <h2>Connect free-tier Gemini</h2>
          <ol>
            <li>
              Create a personal API key in{" "}
              <a
                href="https://aistudio.google.com/apikey"
                target="_blank"
                rel="noreferrer"
              >
                Google AI Studio
              </a>
              . Use a project marked <strong>Free Tier</strong> without enabling
              billing.
            </li>
            <li>
              Open the <code>.env</code> file in the project folder.
            </li>
            <li>
              Set <code>GEMINI_API_KEY</code> to your own API key. Keep it
              private.
            </li>
            <li>
              Restart with{" "}
              <code>docker compose up -d --force-recreate app</code>.
            </li>
            <li>
              Upload a sample document or retry one waiting for a key. Wait for{" "}
              <strong>Ready</strong>.
            </li>
          </ol>
          <p>
            Free-tier quotas apply. This app cannot verify your billing status
            and never falls back to OpenAI. Without a key, the guided SQL
            explorer works.
          </p>
        </article>
        <article className="guide-card">
          <h2>Three ways to ask</h2>
          <dl>
            <dt>Database</dt>
            <dd>
              Customers, delivered purchases, products, inventory and structured
              reviews.
            </dd>
            <dt>Documents</dt>
            <dd>
              Policies, reports and text uploaded to your knowledge library.
            </dd>
            <dt>Combined</dt>
            <dd>
              Database matches enriched with document excerpts associated by
              product and customer IDs.
            </dd>
          </dl>
        </article>
      </div>
      <article className="guide-card">
        <h2>Try the college demonstration</h2>
        <ol>
          <li>
            Run the guided database query: laptops above ₹50,000. The fictional
            seed data has four delivered purchase records.
          </li>
          <li>
            Upload <code>samples/asha-atlas-review.txt</code> with product{" "}
            <strong>201</strong> and customer <strong>101</strong>.
          </li>
          <li>
            Upload the other sample reviews with the IDs listed in{" "}
            <code>samples/README.md</code>, then upload the return policy
            without IDs.
          </li>
          <li>
            Ask the combined example, inspect the records, open a cited excerpt,
            then expand “How this query ran.”
          </li>
        </ol>
      </article>
      <article className="guide-card status-card">
        <h2>Workspace status</h2>
        {health.error && <Notice>{health.error}</Notice>}
        <div>
          {Object.entries(health.data?.shards || {}).map(([n, s]) => (
            <p key={n}>
              <span>{label(n)} database</span>
              <Badge status={s} />
            </p>
          ))}
          <p>
            <span>Document index</span>
            <Badge status={health.data?.qdrant || "CHECKING"} />
          </p>
          <p>
            <span>Cache</span>
            <Badge status={health.data?.redis || "CHECKING"} />
          </p>
          <p>
            <span>Gemini configuration</span>
            <Badge
              status={health.data?.aiConfigured ? "READY" : "NOT_CONFIGURED"}
            />
          </p>
        </div>
        <small>
          Configured means a key is present; a successful provider request is
          still required to verify access.
        </small>
      </article>
      <p className="scope-note">
        This is a local college demonstration with personal accounts and three
        independent PostgreSQL databases. It does not implement a production
        distributed database cluster. Cross-shard reads are not a consistent
        global snapshot. SQL is generated from approved templates; arbitrary SQL
        and writes are not supported.
      </p>
    </>
  );
}
export default App;
