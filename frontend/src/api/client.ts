const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string) || 'http://localhost:8080';

export async function apiFetch<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const url = `${BASE_URL}${endpoint}`;
  try {
    const response = await fetch(url, {
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
      ...options,
    });

    if (!response.ok) {
      throw new Error(`API error ${response.status}: ${response.statusText}`);
    }

    return (await response.json()) as T;
  } catch (err) {
    console.warn(`Fetch to ${url} failed. Using demo fallback data.`, err);
    throw err;
  }
}

export { BASE_URL };
