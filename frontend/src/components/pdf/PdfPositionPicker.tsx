import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Document, Page } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';
import { ChevronLeft, ChevronRight, Loader2 } from 'lucide-react';
import { Button } from '../ui/button';

interface PdfRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

interface Props {
  pdfBlob: Blob;
  onChange: (page: number, rect: PdfRect) => void;
  disabled?: boolean;
}

interface PageDims {
  widthPoints: number;
  heightPoints: number;
  rotate: number;
}

const MIN_RECT_PT = 50;
const DEFAULT_WIDTH_PT = 180;
const DEFAULT_HEIGHT_PT = 60;

export default function PdfPositionPicker({ pdfBlob, onChange, disabled }: Props) {
  const { t } = useTranslation();
  const [numPages, setNumPages] = useState(0);
  const [pageNumber, setPageNumber] = useState(1);
  const [pageDims, setPageDims] = useState<PageDims | null>(null);
  const [drawingRect, setDrawingRect] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const [committedRect, setCommittedRect] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const overlayRef = useRef<HTMLDivElement | null>(null);
  const startRef = useRef<{ x: number; y: number } | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [containerWidth, setContainerWidth] = useState<number>(0);

  const [pdfData, setPdfData] = useState<Uint8Array | null>(null);

  useLayoutEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        setContainerWidth(entry.contentRect.width);
      }
    });
    ro.observe(el);
    setContainerWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    let cancelled = false;
    pdfBlob.arrayBuffer().then((buf) => {
      if (!cancelled) setPdfData(new Uint8Array(buf));
    });
    return () => {
      cancelled = true;
    };
  }, [pdfBlob]);

  const fileMemo = useMemo(() => (pdfData ? { data: pdfData } : null), [pdfData]);

  const handleLoadSuccess = useCallback(({ numPages: total }: { numPages: number }) => {
    setNumPages(total);
    setPageNumber(1);
    setCommittedRect(null);
    setDrawingRect(null);
  }, []);

  const handlePageLoadSuccess = useCallback(
    (page: { originalWidth: number; originalHeight: number; rotate: number }) => {
      // Use originalWidth/originalHeight (PDF user units = points at scale 1).
      // page.width/page.height in react-pdf are the *rendered* pixel size and
      // would collapse the ratio to ~1, sending CSS-pixel values to the backend
      // as if they were PDF points — i.e. signature offset proportional to render scale.
      setPageDims({
        widthPoints: page.originalWidth,
        heightPoints: page.originalHeight,
        rotate: page.rotate,
      });
      setCommittedRect(null);
      setDrawingRect(null);
    },
    [],
  );

  const goPrev = () => {
    if (pageNumber > 1) setPageNumber(pageNumber - 1);
  };
  const goNext = () => {
    if (pageNumber < numPages) setPageNumber(pageNumber + 1);
  };

  const getLocal = (clientX: number, clientY: number) => {
    const el = overlayRef.current;
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    return {
      x: Math.max(0, Math.min(rect.width, clientX - rect.left)),
      y: Math.max(0, Math.min(rect.height, clientY - rect.top)),
    };
  };

  const onMouseDown = (e: React.MouseEvent) => {
    if (disabled) return;
    const pos = getLocal(e.clientX, e.clientY);
    if (!pos) return;
    startRef.current = pos;
    setDrawingRect({ x: pos.x, y: pos.y, w: 0, h: 0 });
  };

  const onMouseMove = (e: React.MouseEvent) => {
    if (!startRef.current) return;
    const pos = getLocal(e.clientX, e.clientY);
    if (!pos) return;
    const x = Math.min(startRef.current.x, pos.x);
    const y = Math.min(startRef.current.y, pos.y);
    const w = Math.abs(pos.x - startRef.current.x);
    const h = Math.abs(pos.y - startRef.current.y);
    setDrawingRect({ x, y, w, h });
  };

  const finishDrag = () => {
    if (!startRef.current || !drawingRect || !pageDims || !overlayRef.current) {
      startRef.current = null;
      setDrawingRect(null);
      return;
    }
    const overlay = overlayRef.current.getBoundingClientRect();
    const ratio = pageDims.widthPoints / overlay.width;

    let cssRect = drawingRect;
    if (cssRect.w * ratio < MIN_RECT_PT || cssRect.h * ratio < MIN_RECT_PT) {
      const cx = cssRect.x + cssRect.w / 2;
      const cy = cssRect.y + cssRect.h / 2;
      const wPx = DEFAULT_WIDTH_PT / ratio;
      const hPx = DEFAULT_HEIGHT_PT / ratio;
      cssRect = {
        x: Math.max(0, Math.min(overlay.width - wPx, cx - wPx / 2)),
        y: Math.max(0, Math.min(overlay.height - hPx, cy - hPx / 2)),
        w: wPx,
        h: hPx,
      };
    }
    setCommittedRect(cssRect);
    startRef.current = null;
    setDrawingRect(null);

    const pdfRect = toPdfRect(cssRect, pageDims, overlay.width, overlay.height);
    onChange(pageNumber, pdfRect);
  };

  return (
    <div className="rounded-md border bg-muted/30">
      <div className="flex items-center justify-between gap-2 border-b px-3 py-2">
        <div className="text-sm font-medium">{t('pades.pickerTitle')}</div>
        <div className="flex items-center gap-1">
          <Button type="button" size="sm" variant="ghost" onClick={goPrev} disabled={pageNumber <= 1 || disabled}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="min-w-[5rem] text-center text-xs text-muted-foreground">
            {numPages > 0 ? `${pageNumber} / ${numPages}` : ''}
          </span>
          <Button type="button" size="sm" variant="ghost" onClick={goNext} disabled={pageNumber >= numPages || disabled}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
      <div className="max-h-[480px] overflow-auto p-3" ref={containerRef}>
        {/* No w-full: the inline-block must shrink to the rendered page width so
            the absolute-positioned overlay sits exactly on top of the page canvas.
            With w-full the overlay was wider than the page and clicks on the
            right edge mapped past the page edge in PDF coords. */}
        <div className="relative inline-block">
          {fileMemo ? (
          <Document
            file={fileMemo}
            onLoadSuccess={handleLoadSuccess}
            loading={
              <div className="flex items-center gap-2 py-12 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> {t('pades.loadingPdf')}
              </div>
            }
            error={
              <div className="py-12 text-sm text-destructive">{t('pades.pdfLoadFailed')}</div>
            }
          >
            <Page
              pageNumber={pageNumber}
              width={containerWidth > 0 ? containerWidth - 24 : undefined}
              onLoadSuccess={handlePageLoadSuccess}
              renderAnnotationLayer={false}
              renderTextLayer={false}
            />
          </Document>
          ) : (
            <div className="flex items-center gap-2 py-12 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> {t('pades.loadingPdf')}
            </div>
          )}
          <div
            ref={overlayRef}
            className="absolute inset-0 cursor-crosshair select-none"
            onMouseDown={onMouseDown}
            onMouseMove={onMouseMove}
            onMouseUp={finishDrag}
            onMouseLeave={() => {
              if (startRef.current) finishDrag();
            }}
          >
            {drawingRect && (
              <div
                className="pointer-events-none absolute border-2 border-primary/80 bg-primary/15"
                style={{
                  left: drawingRect.x,
                  top: drawingRect.y,
                  width: drawingRect.w,
                  height: drawingRect.h,
                }}
              />
            )}
            {!drawingRect && committedRect && (
              <div
                className="pointer-events-none absolute border-2 border-emerald-500 bg-emerald-500/15"
                style={{
                  left: committedRect.x,
                  top: committedRect.y,
                  width: committedRect.w,
                  height: committedRect.h,
                }}
              />
            )}
          </div>
        </div>
      </div>
      <div className="border-t px-3 py-2 text-xs text-muted-foreground">
        {committedRect ? t('pades.pickerSelected', { page: pageNumber }) : t('pades.pickerHint')}
      </div>
    </div>
  );
}

function toPdfRect(
  cssRect: { x: number; y: number; w: number; h: number },
  dims: PageDims,
  canvasW: number,
  canvasH: number,
): PdfRect {
  const ratio = dims.widthPoints / canvasW;
  const verticalRatio = dims.heightPoints / canvasH;
  const xPt = cssRect.x * ratio;
  const wPt = cssRect.w * ratio;
  const hPt = cssRect.h * verticalRatio;
  const yPt = dims.heightPoints - (cssRect.y + cssRect.h) * verticalRatio;

  let x = xPt;
  let y = yPt;
  let w = wPt;
  let h = hPt;
  const pageW = dims.widthPoints;
  const pageH = dims.heightPoints;
  if (x < 0) {
    w += x;
    x = 0;
  }
  if (y < 0) {
    h += y;
    y = 0;
  }
  if (x + w > pageW) w = pageW - x;
  if (y + h > pageH) h = pageH - y;
  return { x, y, w, h };
}
