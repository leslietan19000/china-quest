import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

const args = process.argv.slice(2);
const next = resolve('node_modules/next/dist/bin/next');
const result = spawnSync(process.execPath, [next, ...args], {
  stdio: 'inherit',
  env: {...process.env, NEXT_TELEMETRY_DISABLED: '1'},
});
if (result.error) throw result.error;
process.exit(result.status ?? 1);
