import axios from "axios";
export const api = axios.create({ baseURL: "/api", timeout: 20000 });
let csrfPromise;
export const resetCsrf = () => {
  csrfPromise = null;
};
api.interceptors.request.use(async (config) => {
  if (!["get", "head", "options"].includes(config.method?.toLowerCase())) {
    if (!csrfPromise)
      csrfPromise = api
        .get("/auth/csrf")
        .then((r) => r.data)
        .catch((e) => {
          resetCsrf();
          throw e;
        });
    const csrf = await csrfPromise;
    config.headers[csrf.headerName] = csrf.token;
  }
  return config;
});
api.interceptors.response.use(
  (r) => r,
  (e) => {
    if (e.response?.status === 403) resetCsrf();
    if (e.response?.status === 401 && !e.config?.url?.startsWith("/auth/"))
      window.dispatchEvent(new Event("session-expired"));
    return Promise.reject(e);
  },
);
export const errorMessage = (e) =>
  e.response?.data?.message ||
  (e.code === "ECONNABORTED"
    ? "The request timed out. Refresh the list before trying again."
    : "Unable to reach the application. Check that the backend is running.");
export const label = (value) =>
  String(value ?? "")
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
