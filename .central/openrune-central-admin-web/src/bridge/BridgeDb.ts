import { parseBridgeMessage, sendBridge, type BridgeMessage, type QueryResultPayload } from "../bridgeClient";

let seq = 0;
function nextRequestId(): string {
  seq += 1;
  return `r${Date.now()}-${seq}`;
}

/**
 * Multiplexed request/response over one WebSocket (see `scripts/pg-bridge.mjs`).
 */
export class BridgeDb {
  private readonly pending = new Map<
    string,
    { resolve: (v: QueryResultPayload) => void; reject: (e: Error) => void }
  >();
  private connectWait: { resolve: () => void; reject: (e: Error) => void } | null = null;
  private onMessage: ((ev: MessageEvent) => void) | null = null;

  constructor(private readonly ws: WebSocket) {
    this.onMessage = (ev: MessageEvent) => {
      const msg = parseBridgeMessage(String(ev.data));
      if (msg.type === "error") {
        if (this.connectWait) {
          this.connectWait.reject(new Error(msg.message));
          this.connectWait = null;
          return;
        }
        const id = msg.requestId;
        if (id && this.pending.has(id)) {
          this.pending.get(id)!.reject(new Error(msg.message));
          this.pending.delete(id);
        }
        return;
      }
      if (msg.type === "ready") {
        if (this.connectWait) {
          this.connectWait.resolve();
          this.connectWait = null;
        }
        return;
      }
      if (msg.type === "result") {
        const id = msg.requestId;
        if (id && this.pending.has(id)) {
          this.pending.get(id)!.resolve(msg);
          this.pending.delete(id);
        }
        return;
      }
    };
    ws.addEventListener("message", this.onMessage);
  }

  dispose() {
    if (this.onMessage) {
      this.ws.removeEventListener("message", this.onMessage);
    }
    for (const { reject } of this.pending.values()) {
      reject(new Error("bridge disposed"));
    }
    this.pending.clear();
    if (this.connectWait) {
      this.connectWait.reject(new Error("bridge disposed"));
      this.connectWait = null;
    }
  }

  async connectPg(args: {
    host: string;
    port: number;
    database: string;
    user: string;
    password: string;
    ssl: boolean;
  }): Promise<void> {
    const requestId = nextRequestId();
    const msg: BridgeMessage = {
      type: "connect",
      requestId,
      ...args,
    };
    await new Promise<void>((resolve, reject) => {
      this.connectWait = { resolve, reject };
      sendBridge(this.ws, msg);
    });
  }

  async query(sql: string, params: unknown[] = []): Promise<QueryResultPayload> {
    const requestId = nextRequestId();
    return new Promise((resolve, reject) => {
      this.pending.set(requestId, { resolve, reject });
      sendBridge(this.ws, { type: "query", requestId, sql, params });
    });
  }
}
