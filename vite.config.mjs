import { defineConfig } from 'vite';
import path from 'path';

// The Maven build copies this config + the JS sources into target/frontend and runs Vite there, so
// node_modules stays under target/ and out of the project root. PROJECT_ROOT (passed by Maven) points
// at the real project so the bundle is written to target/classes/static/built. For a direct `npm` run
// from the project root, PROJECT_ROOT is unset and the current working directory is the project root.
export default defineConfig(({ mode }) => {
  const isDev = mode === 'development';
  const projectRoot = process.env.PROJECT_ROOT || process.cwd();

  return {
    // Library mode does NOT auto-replace process.env.NODE_ENV (app mode does), so React would hit
    // "process is not defined" in the browser. Define it at build time so it is inlined as a string
    // literal (and React ships its production build in a prod bundle). Our own code uses
    // import.meta.env.DEV, which Vite statically replaces too.
    define: {
      'process.env.NODE_ENV': JSON.stringify(isDev ? 'development' : 'production'),
    },
    // TypeScript JSX (.tsx) is handled natively by esbuild; use React's automatic runtime so JSX needs
    // no `import React`.
    esbuild: {
      jsx: 'automatic',
    },
    build: {
      // Single self-executing bundle.js served by Thymeleaf (index.html -> @{/built/bundle.js}).
      lib: {
        entry: path.resolve(process.cwd(), 'src/main/js/App.tsx'),
        formats: ['iife'],
        name: 'glm',
        fileName: () => 'bundle.js',
      },
      outDir: path.resolve(projectRoot, 'target/classes/static/built'),
      emptyOutDir: false,
      // Production build is minified (React production build); the dev build stays readable with
      // source maps for debugging.
      minify: !isDev,
      sourcemap: isDev,
    },
  };
});
