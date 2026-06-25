import { enUS, vi } from 'date-fns/locale';
import i18n from './i18n';

export const getDateLocale = () => (i18n.language?.startsWith('vi') ? vi : enUS);
