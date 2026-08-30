import fs from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";

const require = createRequire(import.meta.url);
const sharp = require("sharp");

for (const sourceArgument of process.argv.slice(2)) {
  const source = path.resolve(sourceArgument);
  const destination = path.resolve(
    "app/src/main/res/drawable",
    `${path.basename(source, path.extname(source))}.png`,
  );
  const svg = await fs.readFile(source);
  await sharp(svg, { density: 72 }).png().toFile(destination);
  process.stdout.write(`${destination}\n`);
}
