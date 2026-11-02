# Frontend

Angular 22 single-page application for accounting-assistant: standalone components,
signals and lazy-loaded routes. The user interface is in French (locale `fr-BE`).

See the [main README](../README.md) for the project overview and for running the full
stack with Docker.

## Development

Requires Node.js ≥ 22.22.3, ≥ 24.15.0 or ≥ 26 (Angular CLI 22).

```bash
npm ci
npm start                    # ng serve on http://localhost:4200
npm run build                # production build into dist/
npx ng test --watch=false    # unit tests (Vitest) with coverage
```

`ng serve` proxies `/api` to the Spring Boot backend on <http://localhost:8080>
(`src/proxy.conf.json`); start it from `../backend` with `./gradlew bootRun` and a
reachable PostgreSQL database.
