import { useState } from "react";
import type { BridgeDb } from "../bridge/BridgeDb";
import { AccountsTab } from "./AccountsTab";
import { RealmsTab } from "./RealmsTab";
import { WorldsTab } from "./WorldsTab";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

export type AdminSectionId = "realms" | "worlds" | "accounts";

const nav: { id: AdminSectionId; label: string }[] = [
  { id: "realms", label: "Realms" },
  { id: "worlds", label: "Worlds" },
  { id: "accounts", label: "Accounts" },
];

export function AdminShell({ db, status }: { db: BridgeDb; status: string }) {
  const [section, setSection] = useState<AdminSectionId>("worlds");

  return (
    <Card className="min-h-[calc(100vh-3rem)]">
      <CardHeader className="pb-2">
        <CardTitle className="text-xl">Administration</CardTitle>
        <CardDescription>{status}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col gap-6 md:flex-row md:items-start">
          <nav className="flex w-full flex-col gap-1 border-b border-border pb-4 md:w-44 md:shrink-0 md:border-b-0 md:border-r md:pb-0 md:pr-4">
            {nav.map((item) => (
              <Button
                key={item.id}
                type="button"
                variant={section === item.id ? "secondary" : "ghost"}
                className={cn("justify-start font-normal", section === item.id && "font-medium")}
                onClick={() => setSection(item.id)}
              >
                {item.label}
              </Button>
            ))}
          </nav>
          <div className="min-w-0 flex-1">
            {section === "realms" && <RealmsTab db={db} />}
            {section === "worlds" && <WorldsTab db={db} />}
            {section === "accounts" && <AccountsTab db={db} />}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
