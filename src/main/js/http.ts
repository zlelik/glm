// Minimal fetch-based JSON helpers.
// Send the session cookie (same-origin) and parse the JSON body into the requested type.

function getCookie(name: string): string {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : '';
}

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
  });
  if (!response.ok) {
    throw new Error(`GET ${path} failed with HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}

// POST a JSON body. CSRF is enabled server-side, so echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header
// (same scheme as the logout request). On error, surface the RFC 7807 ProblemDetail "detail" when present.
export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      'X-XSRF-TOKEN': getCookie('XSRF-TOKEN'),
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    let message = `POST ${path} failed with HTTP ${response.status}`;
    try {
      const problem = await response.json();
      if (problem && typeof problem.detail === 'string') {
        message = problem.detail;
      }
    } catch {
      // body was not JSON - keep the generic message
    }
    throw new Error(message);
  }
  // Some endpoints return no body (e.g. add-cell); parse JSON only when there is something to parse.
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
