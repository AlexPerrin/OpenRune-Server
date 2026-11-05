import { useCallback, useEffect, useRef, useState } from "react";
import { AdminShell } from "./admin/AdminShell";
import { bridgeUrlFromWindow } from "./bridgeClient";
import { BridgeDb } from "./bridge/BridgeDb";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type ConnForm = {
  host: string;
  port: string;
  database: string;
  user: string;
  password: string;
  ssl: boolean;
};

const defaultForm = (): ConnForm => ({
  host: "127.0.0.1",
  port: "5432",
  database: "openrune_central",
  user: "postgres",
  password: "",
  ssl: false,
});

type Step = "credentials" | "admin";

export function App() {
  const [step, setStep] = useState<Step>("credentials");
  const [form, setForm] = useState<ConnForm>(defaultForm);
  const [bridgeWsUrl] = useState(() => bridgeUrlFromWindow());
  const [ws, setWs] = useState<WebSocket | null>(null);
  const [db, setDb] = useState<BridgeDb | null>(null);
  const dbRef = useRef<BridgeDb | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const [status, setStatus] = useState<string>("Disconnected");
  const [lastError, setLastError] = useState<string | null>(null);
  const [connectBusy, setConnectBusy] = useState(false);

  useEffect(() => {
    return () => {
      dbRef.current?.dispose();
      dbRef.current = null;
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, []);

  const connectWs = useCallback(() => {
    setLastError(null);
    dbRef.current?.dispose();
    dbRef.current = null;
    setDb(null);
    wsRef.current?.close();
    wsRef.current = null;
    setWs(null);
    setStatus("Connecting…");
    const s = new WebSocket(bridgeWsUrl);
    s.addEventListener("open", () => {
      wsRef.current = s;
      setWs(s);
      const d = new BridgeDb(s);
      dbRef.current = d;
      setDb(d);
      setStep("credentials");
      setStatus("Connected to bridge");
    });
    s.addEventListener("close", () => {
      dbRef.current?.dispose();
      dbRef.current = null;
      wsRef.current = null;
      setDb(null);
      setWs(null);
      setStep("credentials");
      setStatus("Disconnected");
    });
    s.addEventListener("error", () => {
      setLastError(`WebSocket failed (${bridgeWsUrl}). Run npm run dev in openrune-central-admin-web.`);
      setStatus("Error");
    });
  }, [bridgeWsUrl]);

  useEffect(() => {
    connectWs();
  }, [connectWs]);

  const sendDbConnect = useCallback(async () => {
    const d = dbRef.current;
    const sock = wsRef.current;
    if (!d || !sock || sock.readyState !== WebSocket.OPEN) {
      return;
    }
    setLastError(null);
    setConnectBusy(true);
    try {
      await d.connectPg({
        host: form.host.trim(),
        port: Number(form.port) || 5432,
        database: form.database.trim(),
        user: form.user.trim(),
        password: form.password,
        ssl: form.ssl,
      });
      setStep("admin");
      setStatus("Connected");
    } catch (e) {
      setLastError(e instanceof Error ? e.message : String(e));
      setStatus("Connection failed");
    } finally {
      setConnectBusy(false);
    }
  }, [form]);

  return (
    <div className="mx-auto min-h-screen max-w-[1400px] p-6 md:p-8">

      {step === "credentials" && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Database</CardTitle>
            <CardDescription className="flex items-center gap-2">
              <span>{status}</span>
              {!ws && (
                <Button type="button" variant="outline" size="sm" onClick={connectWs}>
                  Retry bridge
                </Button>
              )}
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="db-host">Host</Label>
              <Input id="db-host" value={form.host} onChange={(e) => setForm({ ...form, host: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="db-port">Port</Label>
              <Input id="db-port" value={form.port} onChange={(e) => setForm({ ...form, port: e.target.value })} />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="db-name">Database</Label>
              <Input id="db-name" value={form.database} onChange={(e) => setForm({ ...form, database: e.target.value })} />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="db-user">User</Label>
              <Input id="db-user" value={form.user} onChange={(e) => setForm({ ...form, user: e.target.value })} />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="db-pass">Password</Label>
              <Input
                id="db-pass"
                type="password"
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
              />
            </div>
            <div className="flex items-center gap-2 sm:col-span-2">
              <input
                id="db-ssl"
                type="checkbox"
                className="h-4 w-4 rounded border border-input"
                checked={form.ssl}
                onChange={(e) => setForm({ ...form, ssl: e.target.checked })}
              />
              <Label htmlFor="db-ssl" className="cursor-pointer font-normal text-muted-foreground">
                SSL
              </Label>
            </div>
          </CardContent>
          <CardFooter>
            <Button type="button" disabled={connectBusy || !db || !ws} onClick={sendDbConnect}>
              {connectBusy ? "Connecting…" : "Connect"}
            </Button>
          </CardFooter>
        </Card>
      )}

      {step === "admin" && db && <AdminShell db={db} status={status} />}

      {lastError && (
        <Card className="mt-6 border-destructive/50 bg-destructive/10">
          <CardHeader className="py-4">
            <CardTitle className="text-base text-destructive">Error</CardTitle>
            <CardDescription className="text-destructive/90 whitespace-pre-wrap font-mono text-xs">{lastError}</CardDescription>
          </CardHeader>
        </Card>
      )}
    </div>
  );
}
