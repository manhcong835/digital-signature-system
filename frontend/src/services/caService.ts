import apiClient from './api';

function download(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

export const caService = {
  downloadCaCertificatePem: async (): Promise<void> => {
    const response = await apiClient.get('/ca/certificate', { responseType: 'blob' });
    download(new Blob([response.data], { type: 'application/x-pem-file' }), 'digital-signature-ca.pem');
  },

  downloadCaCertificateDer: async (): Promise<void> => {
    const response = await apiClient.get('/ca/certificate.cer', { responseType: 'blob' });
    download(new Blob([response.data], { type: 'application/pkix-cert' }), 'digital-signature-ca.cer');
  },

  downloadCrl: async (): Promise<void> => {
    const response = await apiClient.get('/ca/crl', { responseType: 'blob' });
    download(new Blob([response.data], { type: 'application/pkix-crl' }), 'digital-signature-ca.crl');
  },
};
