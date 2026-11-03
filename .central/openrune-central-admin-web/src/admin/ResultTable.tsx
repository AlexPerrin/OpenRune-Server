import type { QueryResultPayload } from "../bridgeClient";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

function formatCell(v: unknown): string {
  if (v === null || v === undefined) {
    return "";
  }
  if (typeof v === "object") {
    return JSON.stringify(v);
  }
  return String(v);
}

export function ResultTable({ result }: { result: QueryResultPayload | null }) {
  if (!result || result.fields.length === 0) {
    return null;
  }
  return (
    <div className="mt-2">
      <Table>
        <TableHeader>
          <TableRow>
            {result.fields.map((f) => (
              <TableHead key={f.name} className="whitespace-nowrap">
                {f.name}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {result.rows.map((row, i) => (
            <TableRow key={i}>
              {result.fields.map((f) => (
                <TableCell key={f.name} className="max-w-[240px] truncate font-mono text-xs align-top">
                  {formatCell(row[f.name])}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
