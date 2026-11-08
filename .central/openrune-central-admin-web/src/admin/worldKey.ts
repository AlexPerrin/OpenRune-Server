function toBase64Url(bytes: Uint8Array): string {
  let bin = "";
  bytes.forEach((b) => {
    bin += String.fromCharCode(b);
  });
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Same shape as former Kotlin `generateWorldKeyMaterial` (24 random bytes → URL-safe Base64, SHA-256 of UTF-8 key). */
export async function generateWorldKeyMaterial(): Promise<{ worldsKey: string; sha256Hex: string }> {
  const raw = new Uint8Array(24);
  crypto.getRandomValues(raw);
  const worldsKey = toBase64Url(raw);
  const utf8 = new TextEncoder().encode(worldsKey);
  const buf = await crypto.subtle.digest("SHA-256", utf8);
  const sha256Hex = [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
  return { worldsKey, sha256Hex };
}
