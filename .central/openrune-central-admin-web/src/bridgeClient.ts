export type BridgeMessage =
  | {
      type: "connect";
      requestId: string;
      host: string;
      port: number;
      database: string;
      user: string;
      password: string;
      ssl: boolean;
    }
  | { type: "query"; requestId: string; sql: string; params?: unknown[] }
  | { type: "close"; requestId?: string };

export type QueryResultPayload = {
  type: "result";
  requestId: string | null;
  command: string;
  rowCount: number | null;
  fields: { name: string; dataTypeID: number }[];
  rows: Record<string, unknown>[];
};

export type BridgeResult =
  | { type: "ready"; requestId: string | null }
  | { type: "closed"; requestId: string | null }
  | QueryResultPayload
  | { type: "error"; requestId: string | null; message: string };

export function bridgeUrlFromWindow(): string {
  const u = new URLSearchParams(window.location.search);
  const override = u.get("bridge");
  if (override) {
    return override;
  }
  return "ws://127.0.0.1:18765";
}

export function sendBridge(ws: WebSocket, msg: BridgeMessage) {
  ws.send(JSON.stringify(msg));
}

export function parseBridgeMessage(raw: string): BridgeResult {
  return JSON.parse(raw) as BridgeResult;
}
