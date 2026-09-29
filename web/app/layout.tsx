import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'China Quest · 家长看板',
  description: '本地导出文件的只读家长看板 / Panel familiar de solo lectura',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="zh-CN"><body>{children}</body></html>;
}
