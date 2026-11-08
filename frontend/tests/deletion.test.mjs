import { test, beforeEach, afterEach, after } from "node:test";
import assert from "node:assert/strict";
import { mkdir, writeFile, unlink } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import path from "node:path";
import { JSDOM } from "jsdom";
import { build } from "esbuild";

// Component tests only: no browser, network, user credentials or live database.
const dom = new JSDOM("<!doctype html><html><body></body></html>", {
  url: "http://localhost/",
});
globalThis.window = dom.window;
globalThis.document = dom.window.document;
globalThis.HTMLElement = dom.window.HTMLElement;
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
dom.window.HTMLDialogElement.prototype.showModal = function () {
  this.setAttribute("open", "");
};
const { createElement, act } = await import("react");
const { createRoot } = await import("react-dom/client");
const { MemoryRouter } = await import("react-router-dom");
const cwd = path.resolve(import.meta.dirname, "..");
const output = await build({
  stdin: {
    contents:
      'export { DatabaseActionDialog } from "./src/DatabaseActions.jsx"; export { default as DatabaseStudio } from "./src/DatabaseStudio.jsx"; export { api, resetCsrf } from "./src/api.js";',
    resolveDir: cwd,
    loader: "jsx",
  },
  bundle: true,
  write: false,
  platform: "node",
  format: "esm",
  packages: "external",
  jsx: "automatic",
});
const temp = path.join(cwd, "node_modules/.cache/shardquery-tests");
await mkdir(temp, { recursive: true });
const modulePath = path.join(temp, `deletion-${process.pid}.mjs`);
await writeFile(modulePath, output.outputFiles[0].text);
const { DatabaseActionDialog, DatabaseStudio, api, resetCsrf } = await import(
  pathToFileURL(modulePath)
);
after(async () => {
  await unlink(modulePath);
  dom.window.close();
});
let root, host, calls, backend;
function response(config, data, status = 200) {
  return { config, data, status, statusText: "OK", headers: {} };
}
beforeEach(() => {
  document.body.innerHTML = '<div id="root"></div>';
  host = document.querySelector("#root");
  root = createRoot(host);
  calls = [];
  resetCsrf();
  backend = async (config) => response(config, null, 204);
  api.defaults.adapter = async (config) => {
    if (config.url === "/auth/csrf")
      return response(config, {
        token: "fixture-csrf",
        headerName: "X-CSRF-TOKEN",
      });
    calls.push(config);
    return backend(config);
  };
});
afterEach(async () => {
  await act(async () => root.unmount());
});
const button = (text, scope = host) =>
  [...scope.querySelectorAll("button")].find(
    (b) => b.textContent.trim() === text,
  );
async function click(element) {
  assert.ok(element, "Expected button exists");
  await act(async () => element.click());
}
const action = (title, path, name) => ({ title, method: "delete", path, name });

for (const [title, url, name] of [
  ["Delete table", "/databases/db/tables/table", "student"],
  ["Delete database", "/databases/db", "Students fees"],
]) {
  test(`${title}: explicit confirmation sends the selected name and CSRF, then closes`, async () => {
    let completed = 0,
      closed = 0;
    await act(async () =>
      root.render(
        createElement(DatabaseActionDialog, {
          action: action(title, url, name),
          onDone: () => completed++,
          onClose: () => closed++,
        }),
      ),
    );
    const dialog = host.querySelector("dialog");
    assert.ok(dialog.open);
    assert.match(dialog.textContent, new RegExp(name));
    assert.equal(calls.length, 0, "Opening the dialog never deletes anything");
    const submit = button("Delete permanently", dialog);
    assert.equal(submit.disabled, false, "No hidden exact-text gate");
    await click(submit);
    assert.equal(calls.length, 1);
    assert.equal(calls[0].method, "delete");
    assert.equal(calls[0].url, url);
    assert.deepEqual(JSON.parse(calls[0].data), { confirmation: name });
    assert.equal(calls[0].headers.get("X-CSRF-TOKEN"), "fixture-csrf");
    assert.equal(completed, 1);
    assert.equal(closed, 1);
  });
}
test("Cancel leaves the database unchanged and makes no delete request", async () => {
  let closed = 0;
  await act(async () =>
    root.render(
      createElement(DatabaseActionDialog, {
        action: action("Delete database", "/databases/db", "Keep me"),
        onDone: () => assert.fail("Cancel must not complete deletion"),
        onClose: () => closed++,
      }),
    ),
  );
  await click(button("Cancel"));
  assert.equal(calls.length, 0);
  assert.equal(closed, 1);
});
test("A failed deletion stays open, explains the failure and can be retried", async () => {
  let completed = 0,
    closed = 0;
  backend = async () => {
    throw {
      response: {
        status: 409,
        data: { message: "Database is busy. Please retry." },
      },
    };
  };
  await act(async () =>
    root.render(
      createElement(DatabaseActionDialog, {
        action: action("Delete database", "/databases/db", "Busy database"),
        onDone: () => completed++,
        onClose: () => closed++,
      }),
    ),
  );
  await click(button("Delete permanently"));
  assert.match(host.querySelector('[role="alert"]').textContent, /busy/);
  assert.equal(completed, 0);
  assert.equal(closed, 0);
  assert.equal(button("Delete permanently").disabled, false);
  backend = async (c) => response(c, null, 204);
  await click(button("Delete permanently"));
  assert.equal(completed, 1);
  assert.equal(closed, 1);
});
test("Duplicate submit events produce only one in-flight delete request", async () => {
  let release;
  backend = (config) =>
    new Promise((resolve) => {
      release = () => resolve(response(config, null, 204));
    });
  await act(async () =>
    root.render(
      createElement(DatabaseActionDialog, {
        action: action("Delete table", "/databases/db/tables/t", "student"),
        onDone: () => {},
        onClose: () => {},
      }),
    ),
  );
  await act(async () => {
    const form = host.querySelector("form");
    for (let i = 0; i < 2; i++)
      form.dispatchEvent(
        new dom.window.Event("submit", { bubbles: true, cancelable: true }),
      );
  });
  assert.equal(calls.length, 1);
  assert.equal(button("Applying…").disabled, true);
  await act(async () => release());
});

function workspaceServer() {
  let databases = [
    {
      id: "db1",
      name: "College",
      kind: "LOCAL",
      status: "READY",
      tableCount: 2,
    },
    {
      id: "db2",
      name: "Keep database",
      kind: "LOCAL",
      status: "READY",
      tableCount: 0,
    },
  ];
  let tables = [
    {
      id: "t1",
      name: "student",
      columns: [{ name: "name", type: "TEXT", required: true }],
    },
    {
      id: "t2",
      name: "keep table",
      columns: [{ name: "name", type: "TEXT", required: true }],
    },
  ];
  backend = async (config) => {
    if (config.method === "delete") {
      if (config.url === "/databases/db1/tables/t1") {
        tables = tables.filter((t) => t.id !== "t1");
        databases[0].tableCount = 1;
      } else if (config.url === "/databases/db1")
        databases = databases.filter((d) => d.id !== "db1");
      else assert.fail("Unexpected delete target");
      return response(config, null, 204);
    }
    if (config.url === "/databases")
      return response(config, structuredClone(databases));
    if (config.url === "/databases/db1/tables")
      return response(config, structuredClone(tables));
    if (config.url.includes("/rows?"))
      return response(config, {
        rows: [{ _row_id: 1, name: "Fixture student" }],
        total: 1,
      });
    assert.fail(`Unexpected test request ${config.url}`);
  };
}
async function openWorkspace() {
  workspaceServer();
  await act(async () =>
    root.render(
      createElement(MemoryRouter, null, createElement(DatabaseStudio)),
    ),
  );
  await click(
    [...host.querySelectorAll("button")].find((b) =>
      b.textContent.includes("College"),
    ),
  );
}
test("Deleting a table removes it from the workspace and preserves other tables", async () => {
  await openWorkspace();
  await click(button("student"));
  await click(button("Delete table"));
  await click(button("Delete permanently", host.querySelector("dialog")));
  assert.equal(host.querySelector("dialog"), null);
  assert.equal(button("student"), undefined);
  assert.ok(button("keep table"));
  assert.match(
    host.querySelector('[role="status"]').textContent,
    /student.*deleted/,
  );
  assert.equal(calls.filter((c) => c.method === "delete").length, 1);
});
test("Deleting a database removes it and clears its table editor", async () => {
  await openWorkspace();
  await click(button("student"));
  await click(button("Delete database"));
  await click(button("Delete permanently", host.querySelector("dialog")));
  assert.equal(host.querySelector("dialog"), null);
  assert.equal(button("student"), undefined);
  assert.equal(
    [...host.querySelectorAll(".database-list button")].some((b) =>
      b.textContent.includes("College"),
    ),
    false,
  );
  assert.ok(
    [...host.querySelectorAll(".database-list button")].some((b) =>
      b.textContent.includes("Keep database"),
    ),
  );
  assert.match(
    host.querySelector('[role="status"]').textContent,
    /College.*deleted/,
  );
});
