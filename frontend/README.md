# WattPilot Frontend

React + TypeScript + Vite single-page application for WattPilot.

Product scope, API contract, and architecture are documented in the repository root `CLAUDE.md` and `docs/`.

## Requirements

- Node.js `^20.19.0 || >=22.12.0`
- npm

## Getting Started

```bash
npm install
cp .env.example .env
npm run dev
```

The dev server runs on http://localhost:5173 and calls the backend at `VITE_API_BASE_URL`.

## Scripts

| Script | Description |
| --- | --- |
| `npm run dev` | Start the Vite dev server |
| `npm run build` | Type-check and build production assets into `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | Run ESLint |

## Environment Variables

| Variable | Description | Example |
| --- | --- | --- |
| `VITE_API_BASE_URL` | Base URL of the WattPilot backend REST API | `http://localhost:8080/api/v1` |

Only `VITE_`-prefixed variables are exposed to the browser bundle, so no secret may be stored here.
`.env` is git-ignored; only `.env.example` is committed.

The frontend keeps its own `.env` instead of using the repository-root `.env`, because Vite loads
environment files from the frontend project directory and the root file holds backend-only values.

## Project Structure

The application uses a feature-oriented structure that mirrors the backend domain modules
described in `docs/tech-stack-architecture.md`.

```text
src/
├─ main.tsx           # Application entry point
├─ App.tsx            # Root component
├─ index.css          # Global styles
├─ vite-env.d.ts      # Typed environment variables
├─ app/               # Router and global providers
├─ pages/             # Route-level screens
├─ features/          # Domain modules
│  └─ <domain>/
│     ├─ api/         # Endpoint functions and query hooks
│     ├─ components/  # Domain-specific components
│     └─ types.ts     # Domain types
├─ components/        # Shared components (ui/ holds shadcn/ui primitives)
├─ lib/               # API client, formatting and other helpers
└─ types/             # Shared API types
```

Feature modules follow the backend domains and the OpenAPI tags:

`auth`, `ev`, `electricity`, `charging`, `schedule`, `history`, `savings`

Directories under `src/` are created together with the first file that belongs to them, so the
repository contains no empty placeholder folders.

## Path Alias

`@/` resolves to `src/`, configured in `tsconfig.app.json` (`paths`) and `vite.config.ts`
(`resolve.alias`).

```ts
import { apiClient } from '@/lib/api-client'
```
