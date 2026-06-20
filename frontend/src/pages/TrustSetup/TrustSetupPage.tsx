import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Shield, Download, Info, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card';
import { caService } from '../../services/caService';
import { toast } from '../../store/toastStore';
import { localizeError } from '../../utils/errorMapper';

export default function TrustSetupPage() {
  const { t } = useTranslation();
  const [downloading, setDownloading] = useState<'pem' | 'cer' | 'crl' | null>(null);

  const handleDownload = async (kind: 'pem' | 'cer' | 'crl') => {
    setDownloading(kind);
    try {
      if (kind === 'pem') await caService.downloadCaCertificatePem();
      else if (kind === 'cer') await caService.downloadCaCertificateDer();
      else await caService.downloadCrl();
    } catch (err: any) {
      toast.error(t('trustSetup.downloadFailed'), localizeError(err?.response?.data?.message));
    } finally {
      setDownloading(null);
    }
  };

  return (
    <div className="container mx-auto max-w-4xl px-4 py-8">
      <div className="mb-6 flex items-start gap-3">
        <Shield className="h-8 w-8 text-primary mt-1" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('trustSetup.title')}</h1>
          <p className="text-muted-foreground">{t('trustSetup.subtitle')}</p>
        </div>
      </div>

      <Card className="mb-4 border-amber-300 bg-amber-50/50">
        <CardContent className="flex items-start gap-3 py-4">
          <Info className="h-5 w-5 text-amber-600 mt-0.5 shrink-0" />
          <div className="text-sm">
            <p className="font-medium text-amber-900">{t('trustSetup.warningTitle')}</p>
            <p className="text-amber-800 mt-1">{t('trustSetup.warningBody')}</p>
          </div>
        </CardContent>
      </Card>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>{t('trustSetup.downloadTitle')}</CardTitle>
          <CardDescription>{t('trustSetup.downloadDesc')}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          <Button onClick={() => handleDownload('cer')} disabled={downloading !== null}>
            {downloading === 'cer' ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Download className="mr-2 h-4 w-4" />}
            {t('trustSetup.downloadCer')}
          </Button>
          <Button variant="outline" onClick={() => handleDownload('pem')} disabled={downloading !== null}>
            {downloading === 'pem' ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Download className="mr-2 h-4 w-4" />}
            {t('trustSetup.downloadPem')}
          </Button>
          <Button variant="outline" onClick={() => handleDownload('crl')} disabled={downloading !== null}>
            {downloading === 'crl' ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Download className="mr-2 h-4 w-4" />}
            {t('trustSetup.downloadCrl')}
          </Button>
        </CardContent>
      </Card>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>{t('trustSetup.adobeTitle')}</CardTitle>
          <CardDescription>{t('trustSetup.adobeDesc')}</CardDescription>
        </CardHeader>
        <CardContent>
          <ol className="list-decimal list-inside space-y-2 text-sm text-muted-foreground">
            <li>{t('trustSetup.adobeStep1')}</li>
            <li>{t('trustSetup.adobeStep2')}</li>
            <li>{t('trustSetup.adobeStep3')}</li>
            <li>{t('trustSetup.adobeStep4')}</li>
            <li>{t('trustSetup.adobeStep5')}</li>
            <li>{t('trustSetup.adobeStep6')}</li>
          </ol>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('trustSetup.afterTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          <p>{t('trustSetup.afterBody')}</p>
        </CardContent>
      </Card>
    </div>
  );
}
