import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const frontendRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = join(frontendRoot, '..');
const envDir = join(frontendRoot, 'src/environments');
const envFile = join(repoRoot, '.env');

function readDotEnv(path) {
  const vars = {};
  try {
    const text = readFileSync(path, 'utf8');
    for (const line of text.split(/\r?\n/)) {
      const match = line.match(/^\s*([^#][^=]+)=(.*)$/);
      if (match) {
        vars[match[1].trim()] = match[2].trim();
      }
    }
  } catch {
    /* ignore missing .env */
  }
  return vars;
}

const dotenv = readDotEnv(envFile);
const supabaseUrl = process.env.SUPABASE_URL ?? dotenv.SUPABASE_URL ?? 'https://YOUR_PROJECT.supabase.co';
const supabasePublishableKey =
  process.env.SUPABASE_PUBLISHABLE_KEY ?? dotenv.SUPABASE_PUBLISHABLE_KEY ?? '';
const apiBaseUrl = process.env.API_BASE_URL ?? dotenv.API_BASE_URL ?? '';

if (supabaseUrl.includes('YOUR_PROJECT') || !supabasePublishableKey) {
  console.warn(
    'Warning: SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY missing in .env — login will fail with "Failed to fetch".',
  );
}

const content = `export const environment = {
  production: false,
  supabaseUrl: ${JSON.stringify(supabaseUrl)},
  supabasePublishableKey: ${JSON.stringify(supabasePublishableKey)},
  apiBaseUrl: ${JSON.stringify(apiBaseUrl)},
};
`;

writeFileSync(join(envDir, 'environment.ts'), content, 'utf8');
console.log('Generated src/environments/environment.ts from .env');
