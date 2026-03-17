export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  validationErrors?: Record<string, string>;

  constructor(message: string, status: number, validationErrors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.validationErrors = validationErrors;
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, buildRequestInit(init));

  if (!response.ok) {
    throw await buildApiError(response);
  }

  return response.json() as Promise<T>;
}

export async function apiFetchBlob(path: string, init?: RequestInit) {
  const response = await fetch(`${API_BASE_URL}${path}`, buildRequestInit(init));

  if (!response.ok) {
    throw await buildApiError(response);
  }

  return response.blob();
}

function buildRequestInit(init?: RequestInit): RequestInit {
  const headers = new Headers(init?.headers ?? {});
  if (!headers.has("Content-Type") && init?.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  return {
    credentials: "include",
    ...init,
    headers,
  };
}

async function buildApiError(response: Response) {
  let errorMessage = `API request failed with status ${response.status}`;
  let validationErrors: Record<string, string> | undefined;

  try {
    const payload = (await response.json()) as {
      message?: string;
      validationErrors?: Record<string, string>;
    };
    errorMessage = payload.message ?? errorMessage;
    validationErrors = payload.validationErrors;
  } catch {
    // Ignore JSON parsing errors for non-JSON responses.
  }

  return new ApiError(errorMessage, response.status, validationErrors);
}
