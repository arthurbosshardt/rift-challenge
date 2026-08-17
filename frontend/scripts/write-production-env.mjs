import { copyFileSync, existsSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const envDir = join(root, 'src/environments');
const envTs = join(envDir, 'environment.ts');
const exampleTs = join(envDir, 'environment.example.ts');

if (!existsSync(envTs) && existsSync(exampleTs)) {
  copyFileSync(exampleTs, envTs);
  console.log('Created src/environments/environment.ts from example');
}

const supabaseUrl = process.env.SUPABASE_URL ?? 'https://YOUR_PROJECT.supabase.co';
const supabasePublishableKey = process.env.SUPABASE_PUBLISHABLE_KEY ?? '';
const apiBaseUrl = process.env.API_BASE_URL ?? '';

const content = `export const environment = {
  production: true,
  supabaseUrl: '${supabaseUrl}',
  supabasePublishableKey: '${supabasePublishableKey}',
  apiBaseUrl: '${apiBaseUrl}',
};
`;

writeFileSync(join(envDir, 'environment.prod.ts'), content, 'utf8');
console.log('Generated src/environments/environment.prod.ts');
