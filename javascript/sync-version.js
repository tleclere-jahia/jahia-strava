// Sets the "version" of package.json to the version given as argument (the pom.xml version).
// Usage: node sync-version.js <version>
import fs from "node:fs";

const version = process.argv[2];
if (!version) {
  console.error("usage: node sync-version.js <version>");
  process.exit(1);
}

const file = new URL("package.json", import.meta.url);
const pkg = fs.readFileSync(file, "utf8");
// The first "version" key is the top-level one; rewriting the line keeps the formatting.
const updated = pkg.replace(/"version": "[^"]*"/, `"version": ${JSON.stringify(version)}`);
fs.writeFileSync(file, updated);
