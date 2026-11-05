/**
 * Local WebSocket → PostgreSQL bridge (127.0.0.1 only).
 * Browsers cannot open the Postgres wire protocol; run this alongside the SPA.
 *
 *   cd openrune-central-admin-web && npm install && npm run pg-bridge
 *
 * Default: ws://127.0.0.1:18765  (override with PORT=...)
 */
import pg from "pg";
import { WebSocketServer } from "ws";

const { Client } = pg;
const port = Number(process.env.PORT || 18765);
const host = process.env.BRIDGE_HOST || "127.0.0.1";

const wss = new WebSocketServer({ host, port });
console.log(`openrune pg-bridge listening on ws://${host}:${port}`);

function send(ws, obj) {
  ws.send(JSON.stringify(obj));
}

wss.on("connection", (ws) => {
  /** @type {import('pg').Client | null} */
  let client = null;

  ws.on("message", async (raw) => {
    let msg;
    try {
      msg = JSON.parse(String(raw));
    } catch {
      send(ws, { type: "error", requestId: null, message: "invalid JSON" });
      return;
    }
    const requestId = msg.requestId ?? null;
    try {
      if (msg.type === "connect") {
        if (client) {
          await client.end().catch(() => {});
          client = null;
        }
        const ssl =
          msg.ssl === true || msg.ssl === "require" || msg.ssl === "1"
            ? { rejectUnauthorized: false }
            : false;
        const c = new Client({
          host: String(msg.host || "127.0.0.1"),
          port: Number(msg.port || 5432),
          database: String(msg.database || "postgres"),
          user: String(msg.user || "postgres"),
          password: String(msg.password ?? ""),
          ssl,
        });
        await c.connect();
        client = c;
        send(ws, { type: "ready", requestId });
        return;
      }
      if (msg.type === "query") {
        if (!client) {
          send(ws, { type: "error", requestId, message: "not connected; send connect first" });
          return;
        }
        const sql = String(msg.sql ?? "");
        const params = Array.isArray(msg.params) ? msg.params : [];
        const r = await client.query({ text: sql, values: params });
        send(ws, {
          type: "result",
          requestId,
          command: r.command,
          rowCount: r.rowCount,
          fields: (r.fields || []).map((f) => ({ name: f.name, dataTypeID: f.dataTypeID })),
          rows: r.rows,
        });
        return;
      }
      if (msg.type === "close") {
        if (client) {
          await client.end().catch(() => {});
          client = null;
        }
        send(ws, { type: "closed", requestId });
        return;
      }
      send(ws, { type: "error", requestId, message: "unknown message type" });
    } catch (e) {
      send(ws, { type: "error", requestId, message: e instanceof Error ? e.message : String(e) });
    }
  });
  ws.on("close", async () => {
    if (client) {
      await client.end().catch(() => {});
      client = null;
    }
  });
});
