import i18n from '../i18n/i18n';

const MAP: Record<string, string> = {
  'Invalid signature': 'errors.invalidSignature',
  'Wrong password': 'errors.decryptFailed',
  'Wrong password or corrupted key': 'errors.decryptFailed',
  'File not found': 'errors.fileNotFound',
  'Unauthorized': 'errors.unauthorized',
  'Invalid email or password': 'errors.invalidCredentials',
  'PDF file is required': 'errors.pdfRequired',
  'Key has no certificate': 'errors.keyMissingCert',
  'Signature does not have an embedded signed PDF': 'errors.padesNotFound',
};

const PREFIX_MAP: Array<[RegExp, string]> = [
  [/^PDF signing failed/i, 'errors.pdfSignFailed'],
  [/^Failed to issue certificate/i, 'errors.caIssueFailed'],
  [/^Failed to build signing-certificate/i, 'errors.pdfSignFailed'],
  [/signature rect/i, 'errors.rectInvalid'],
];

export function localizeError(raw?: string | null, fallbackKey = 'errors.generic'): string {
  const t = i18n.t.bind(i18n) as (k: string) => string;
  if (!raw) return t(fallbackKey);
  const trimmed = raw.trim();
  const exact = MAP[trimmed];
  if (exact) return t(exact);
  for (const [pattern, key] of PREFIX_MAP) {
    if (pattern.test(trimmed)) return t(key);
  }
  return trimmed;
}
