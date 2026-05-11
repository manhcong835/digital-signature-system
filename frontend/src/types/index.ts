export interface User {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  organization?: string;
  createdAt?: string;
  updatedAt?: string;
  mustChangePassword?: boolean;
}

export type OtpCodeType = 'REGISTRATION' | 'LOGIN_2FA';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
  organization?: string;
}

export interface AuthResponse {
  token?: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
  user?: User;
  requiresEmailVerification?: boolean;
  requires2fa?: boolean;
  challengeToken?: string;
}

export interface VerifyEmailRequest {
  email: string;
  code: string;
}

export interface Verify2faRequest {
  challengeToken: string;
  code: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResendOtpRequest {
  email: string;
  type: OtpCodeType;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface KeyPair {
  id: number;
  name: string;
  publicKey?: string;
  algorithm: string;
  isDefault: boolean;
  createdAt: string;
  revokedAt?: string;
  revoked: boolean;
  certSerialNumber?: string;
  certIssuedAt?: string;
  certExpiresAt?: string;
  hasCertificate?: boolean;
}

export interface Document {
  id: number;
  originalName: string;
  mimeType?: string;
  size: number;
  sha512Hash: string;
  createdAt: string;
  status: string;
  currentPadesPdfPath?: string;
}

export interface SignatureRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface Signature {
  id: number;
  documentId: number;
  documentName?: string;
  keyId: number;
  keyName?: string;
  signerEmail?: string;
  signerName?: string;
  signatureValue?: string;
  algorithm: string;
  documentHash: string;
  signedAt: string;
  verifyStatus?: string;
  padesPdfAvailable?: boolean;
  signaturePage?: number;
  sigRectX?: number;
  sigRectY?: number;
  sigRectW?: number;
  sigRectH?: number;
}

export interface PadesVerifyResponse {
  signatureCount: number;
  allSignaturesValid: boolean;
  signatures: PadesVerifySignature[];
}

export interface PadesVerifySignature {
  signerCn?: string;
  issuerCn?: string;
  signedAt?: string;
  byteRangeCoversFile: boolean;
  incrementalUpdateValid: boolean;
  cryptoValid: boolean;
  certChainValid: boolean;
  certTrustedByLocalCa: boolean;
  certNotBefore?: string;
  certNotAfter?: string;
  error?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp?: string;
}

export interface VerifyResponse {
  valid: boolean;
  status?: string;
  message?: string;
  algorithm?: string;
  documentName?: string;
  expectedHash?: string;
  computedHash?: string;
  hashMatches?: boolean;
  signatureValid?: boolean;
  signerEmail?: string;
  signerName?: string;
  keyName?: string;
  signedAt?: string;
  verifiedAt?: string;
  signatures?: VerifyResponse[];
}

export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  path?: string;
  validationErrors?: Record<string, string>;
  timestamp?: string;
}

export interface VerificationHistory {
  id: number;
  signatureId: number;
  signerName?: string;
  valid: boolean;
  status: string;
  message?: string;
  computedHash?: string;
  verifiedAt: string;
  verifierEmail?: string;
}
