import { createContext, useContext, useEffect, useState } from "react";
import {
  ArrowRight,
  Database,
  FileText,
  ShieldCheck,
  LogOut,
  Loader2,
} from "lucide-react";
import { api, errorMessage, resetCsrf } from "./api";
const Context = createContext(null);
export const useAuth = () => useContext(Context);
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  async function load() {
    setLoading(true);
    setError("");
    try {
      setUser((await api.get("/auth/me")).data);
    } catch (e) {
      if (e.response?.status !== 401) setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    load();
    const expire = () => {
      setUser(null);
      resetCsrf();
    };
    window.addEventListener("session-expired", expire);
    return () => window.removeEventListener("session-expired", expire);
  }, []);
  async function authenticate(mode, form) {
    const result = await api.post("/auth/" + mode, form);
    resetCsrf();
    setUser(result.data);
  }
  async function logout() {
    await api.post("/auth/logout");
    resetCsrf();
    setUser(null);
    window.location.hash = "/";
  }
  if (loading)
    return (
      <div className="auth-loading">
        <Loader2 className="spin" /> Opening your workspace…
      </div>
    );
  if (error)
    return (
      <div className="auth-loading">
        <p role="alert">{error}</p>
        <button onClick={load}>Try again</button>
      </div>
    );
  return (
    <Context.Provider value={{ user, authenticate, logout }}>
      {user ? children : <AuthScreen />}
    </Context.Provider>
  );
}
function AuthScreen() {
  const { authenticate } = useAuth();
  const [mode, setMode] = useState("login"),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ name: "", email: "", password: "" });
  const register = mode === "register";
  const field = (key) => ({
    value: form[key],
    onChange: (e) => setForm({ ...form, [key]: e.target.value }),
  });
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await authenticate(
        mode,
        register ? form : { email: form.email, password: form.password },
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth-layout">
      <section className="auth-story">
        <div className="auth-wordmark">
          ShardQuery <span>AI</span>
        </div>
        <div>
          <span className="eyebrow">YOUR DATA. YOUR WORKSPACE.</span>
          <h1>
            Start with data.
            <br />
            Find the answer.
          </h1>
          <p>
            A personal place for your databases, documents and the questions
            that connect them.
          </p>
          <div className="auth-features">
            <span>
              <Database size={19} /> Create databases and tables
            </span>
            <span>
              <FileText size={19} /> Keep your documents together
            </span>
            <span>
              <ShieldCheck size={19} /> Access your own private workspace
            </span>
          </div>
        </div>
        <small>SQL + documents, connected through Gemini.</small>
      </section>
      <section className="auth-panel">
        <div className="auth-form-wrap">
          <span className="eyebrow">WELCOME TO SHARDQUERY</span>
          <h2>{register ? "Create your account" : "Welcome back"}</h2>
          <p>
            {register
              ? "Set up your personal workspace. No invitation token needed."
              : "Sign in to continue working with your data."}
          </p>
          <form onSubmit={submit} className="stack-form">
            {register && (
              <label>
                Full name
                <input
                  autoComplete="name"
                  required
                  minLength={2}
                  maxLength={80}
                  {...field("name")}
                />
              </label>
            )}
            <label>
              Email address
              <input
                type="email"
                autoComplete="username"
                required
                maxLength={254}
                {...field("email")}
              />
            </label>
            <label>
              Password
              <input
                type="password"
                autoComplete={register ? "new-password" : "current-password"}
                required
                minLength={register ? 12 : 1}
                maxLength={72}
                {...field("password")}
              />
              {register && (
                <small>
                  Use at least 12 characters. A memorable passphrase works well.
                </small>
              )}
            </label>
            {error && (
              <div className="notice" role="alert">
                {error}
              </div>
            )}
            <button className="primary" disabled={busy}>
              {busy ? (
                <Loader2 size={17} className="spin" />
              ) : (
                <ArrowRight size={17} />
              )}
              {busy ? "Please wait…" : register ? "Create account" : "Sign in"}
            </button>
          </form>
          <p className="auth-switch">
            {register ? "Already have an account?" : "New to ShardQuery?"}{" "}
            <button
              disabled={busy}
              onClick={() => {
                setMode(register ? "login" : "register");
                setError("");
                setForm({ ...form, password: "" });
              }}
            >
              {register ? "Sign in" : "Create an account"}
            </button>
          </p>
          <small className="auth-footnote">
            Accounts are stored on this installation. Your data stays separate
            from other accounts. Gemini receives the relevant data when you run
            an AI query or index a document.
          </small>
        </div>
      </section>
    </main>
  );
}
export function AccountMenu() {
  const { user, logout } = useAuth();
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <div className="account-menu">
      <span title={user.email}>{user.name}</span>
      <button
        title="Sign out"
        aria-label="Sign out"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          try {
            await logout();
          } catch (e) {
            setError(errorMessage(e));
          } finally {
            setBusy(false);
          }
        }}
      >
        <LogOut size={16} />
      </button>
      {error && <span role="alert">{error}</span>}
    </div>
  );
}
