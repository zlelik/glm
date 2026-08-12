import log from 'loglevel';

// loglevel returns the native console methods bound to `console`, so DevTools still shows the real call
// site (file:line) instead of a line inside this module - the main reason to use it over a hand-rolled
// wrapper. In development everything down to debug is shown; in production only warnings and errors.
log.setLevel(import.meta.env.DEV ? 'debug' : 'warn');

/**
 * Returns a bracketed local timestamp with millisecond precision, e.g. "[2026-06-25 15:35:56.123]".
 *
 * Pass it as the FIRST argument to a log call - log.debug(ts(), 'message', someObject) - rather than
 * concatenating it into the message. Because it is evaluated as an argument (before the bound console
 * method is invoked), the clickable call-site line number is preserved, and any object argument stays
 * expandable in the console instead of being stringified.
 */
export function ts(): string {
  const d = new Date();
  const pad = (n: number, width = 2) => String(n).padStart(width, '0');
  return `[${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
       + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}]`;
}

export default log;
