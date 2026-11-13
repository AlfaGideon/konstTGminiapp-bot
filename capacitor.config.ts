import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'ru.botcraft.studio',
  appName: 'Botcraft Studio',
  webDir: 'dist',
  bundledWebRuntime: false,
  backgroundColor: '#f5f7fb',
  android: { allowMixedContent: false },
};

export default config;
