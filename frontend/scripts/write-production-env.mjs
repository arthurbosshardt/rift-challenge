import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const envDir = join(root, 'src/environments');

const supabaseUrl = process.env.SUPABASE_URL ?? 'https://YOUR_PROJECT.supabase.co';
const supabasePublishableKey = process.env.SUPABASE_PUBLISHABLE_KEY ?? '';
const apiBaseUrl = process.env.API_BASE_URL ?? 'https://YOUR_BACKEND.onrender.com';

const content = `export const environment = {
  production: true,
  supabaseUrl: '${supabaseUrl}',
  supabasePublishableKey: '${supabasePublishableKey}',
  apiBaseUrl: '${apiBaseUrl}',
};
`;

for (const fileName of ['environment.ts', 'environment.prod.ts']) {
  writeFileSync(join(envDir, fileName), content, 'utf8');
  console.log(`Generated src/environments/${fileName}`);
}
