package syber.security.digitalsignature.signature.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class PdfSigningServiceImpl implements PdfSigningService {

    private static final String DIGEST_ALG = "SHA-512";
    private static final String SIG_ALG = "SHA512withRSA";
    private static final long LARGE_FILE_THRESHOLD = 5L * 1024 * 1024;

    @Value("${pades.output-dir:./uploads/signed-pdf}")
    private String outputDir;

    @Override
    public Path signPdf(Path inputPdf, SignRequest req) throws IOException {
        Path outDir = Paths.get(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(outDir);
        Path output = outDir.resolve(UUID.randomUUID() + ".pdf");

        long size = Files.size(inputPdf);
        log.debug("Signing PDF {} ({} bytes) -> {}", inputPdf, size, output);

        try (PDDocument doc = loadDocument(inputPdf, size);
             OutputStream fos = Files.newOutputStream(output)) {

            int pageCount = doc.getNumberOfPages();
            int targetPageIndex = clampPage(req.getPage(), pageCount) - 1;
            PDPage targetPage = doc.getPage(targetPageIndex);
            PDRectangle pageRect = targetPage.getMediaBox();

            PDRectangle widgetRect;
            PDAppearanceDictionary appearanceDict = null;
            if (req.isVisible()) {
                float[] clamped = clampRect(
                        (float) req.getRectX(), (float) req.getRectY(),
                        (float) req.getRectW(), (float) req.getRectH(),
                        pageRect);
                widgetRect = new PDRectangle(clamped[0], clamped[1], clamped[2], clamped[3]);
            } else {
                // Invisible signature: zero-size rect, no appearance stream.
                // Adobe still lists the signature in the Signature Panel, but
                // nothing is drawn on the page.
                widgetRect = new PDRectangle(0, 0, 0, 0);
            }

            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(COSName.getPDFName("ETSI.CAdES.detached"));
            signature.setName(req.getSignerName());
            if (req.getReason() != null) signature.setReason(req.getReason());
            if (req.getLocation() != null) signature.setLocation(req.getLocation());
            signature.setSignDate(Calendar.getInstance());

            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                acroForm = new PDAcroForm(doc);
                doc.getDocumentCatalog().setAcroForm(acroForm);
            }
            acroForm.setSignaturesExist(true);
            acroForm.setAppendOnly(true);

            PDSignatureField sigField = new PDSignatureField(acroForm);
            sigField.setPartialName("Signature_" + System.currentTimeMillis());
            sigField.setValue(signature);

            PDAnnotationWidget widget = sigField.getWidgets().get(0);
            widget.setRectangle(widgetRect);
            widget.setPage(targetPage);
            widget.setPrinted(true);

            if (req.isVisible()) {
                PDAppearanceStream appearance = buildAppearance(doc, widgetRect, req);
                appearanceDict = new PDAppearanceDictionary();
                appearanceDict.setNormalAppearance(appearance);
                widget.setAppearance(appearanceDict);
            }

            targetPage.getAnnotations().add(widget);
            acroForm.getFields().add(sigField);

            // Mark every COS object touched by this incremental update so PDFBox
            // actually writes them to the appended xref/body. Without this, the
            // saved revision contains the new /Sig dict but no widget — Adobe
            // then reports the field as "(invisible signature)".
            targetPage.getCOSObject().setNeedToBeUpdated(true);
            org.apache.pdfbox.cos.COSBase annotsBase = targetPage.getCOSObject().getDictionaryObject(COSName.ANNOTS);
            if (annotsBase instanceof org.apache.pdfbox.cos.COSArray annotsArray) {
                annotsArray.setNeedToBeUpdated(true);
            }
            widget.getCOSObject().setNeedToBeUpdated(true);
            sigField.getCOSObject().setNeedToBeUpdated(true);
            acroForm.getCOSObject().setNeedToBeUpdated(true);
            doc.getDocumentCatalog().getCOSObject().setNeedToBeUpdated(true);

            doc.addSignature(signature);

            // PDFBox's addSignature(PDSignature) calls prepareNonVisibleSignature
            // internally, which RESETS the widget rectangle to [0 0 0 0] and clears
            // the appearance — that's exactly what we want for invisible signatures,
            // but for visible ones we have to re-apply our rect + appearance afterwards.
            if (req.isVisible()) {
                widget.setRectangle(widgetRect);
                widget.setAppearance(appearanceDict);
                widget.getCOSObject().setNeedToBeUpdated(true);
            }

            ExternalSigningSupport externalSigning = doc.saveIncrementalForExternalSigning(fos);
            byte[] cmsBytes = generateCms(externalSigning.getContent().readAllBytes(), req);
            externalSigning.setSignature(cmsBytes);
        } catch (Exception e) {
            try { Files.deleteIfExists(output); } catch (Exception ignored) {}
            throw new IOException("PDF signing failed: " + e.getMessage(), e);
        }

        log.info("PAdES signed PDF written: {}", output);
        return output;
    }

    private PDDocument loadDocument(Path input, long size) throws IOException {
        if (size > LARGE_FILE_THRESHOLD) {
            return org.apache.pdfbox.Loader.loadPDF(new RandomAccessReadBufferedFile(input.toFile()));
        }
        return org.apache.pdfbox.Loader.loadPDF(input.toFile());
    }

    private int clampPage(int requested, int max) {
        if (requested < 1) return 1;
        if (requested > max) return max;
        return requested;
    }

    private float[] clampRect(float x, float y, float w, float h, PDRectangle page) {
        float minSize = 50f;
        if (w < minSize) w = minSize;
        if (h < minSize) h = minSize;
        float pw = page.getWidth();
        float ph = page.getHeight();
        if (w > pw) w = pw;
        if (h > ph) h = ph;
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + w > pw) x = pw - w;
        if (y + h > ph) y = ph - h;
        return new float[]{x, y, w, h};
    }

    private PDAppearanceStream buildAppearance(PDDocument doc, PDRectangle rect, SignRequest req) throws IOException {
        PDAppearanceStream appearance = new PDAppearanceStream(doc);
        appearance.setBBox(new PDRectangle(0, 0, rect.getWidth(), rect.getHeight()));
        appearance.setResources(new PDResources());
        appearance.setFormType(1);

        PDFont font;
        PDFont boldFont;
        try (InputStream regularIs = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf");
             InputStream boldIs = getClass().getResourceAsStream("/fonts/NotoSans-Bold.ttf")) {
            if (regularIs != null && boldIs != null) {
                font = PDType0Font.load(doc, regularIs);
                boldFont = PDType0Font.load(doc, boldIs);
            } else {
                log.warn("NotoSans fonts not found on classpath, falling back to Helvetica (no Vietnamese support)");
                font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            }
        }

        try (PDPageContentStream cs = new PDPageContentStream(doc, appearance)) {
            cs.setStrokingColor(new PDColor(new float[]{0.1f, 0.2f, 0.6f}, PDDeviceRGB.INSTANCE));
            cs.setLineWidth(0.5f);
            cs.addRect(0.5f, 0.5f, rect.getWidth() - 1f, rect.getHeight() - 1f);
            cs.stroke();

            float padding = 6f;
            float fontSize = Math.max(7f, Math.min(11f, rect.getHeight() / 6f));
            float lineHeight = fontSize + 2f;
            float y = rect.getHeight() - padding - fontSize;

            cs.setNonStrokingColor(new PDColor(new float[]{0.1f, 0.2f, 0.6f}, PDDeviceRGB.INSTANCE));
            cs.beginText();
            cs.setFont(boldFont, fontSize);
            cs.newLineAtOffset(padding, y);
            cs.showText("Digitally signed by");
            cs.endText();
            y -= lineHeight;

            cs.setNonStrokingColor(new PDColor(new float[]{0f, 0f, 0f}, PDDeviceRGB.INSTANCE));
            cs.beginText();
            cs.setFont(boldFont, fontSize);
            cs.newLineAtOffset(padding, y);
            cs.showText(truncate(req.getSignerName(), 40));
            cs.endText();
            y -= lineHeight;

            if (req.getSignerEmail() != null && !req.getSignerEmail().isBlank()) {
                cs.beginText();
                cs.setFont(font, fontSize - 1f);
                cs.newLineAtOffset(padding, y);
                cs.showText(truncate(req.getSignerEmail(), 50));
                cs.endText();
                y -= lineHeight;
            }

            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new java.util.Date());
            cs.beginText();
            cs.setFont(font, fontSize - 1f);
            cs.newLineAtOffset(padding, y);
            cs.showText("Date: " + dateStr);
            cs.endText();
            y -= lineHeight;

            if (req.getReason() != null && !req.getReason().isBlank() && y > padding + fontSize) {
                cs.beginText();
                cs.setFont(font, fontSize - 1f);
                cs.newLineAtOffset(padding, y);
                cs.showText("Reason: " + truncate(req.getReason(), 40));
                cs.endText();
            }
        }
        return appearance;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private byte[] generateCms(byte[] content, SignRequest req) throws Exception {
        List<X509Certificate> chain = new ArrayList<>();
        chain.add(req.getSignerCert());
        if (req.getCaCert() != null) chain.add(req.getCaCert());

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        DigestCalculatorProvider digestProvider = new JcaDigestCalculatorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME).build();

        ContentSigner signer = new JcaContentSignerBuilder(SIG_ALG)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(req.getSignerPrivateKey());

        EssSignedAttributeTableGenerator essGenerator = new EssSignedAttributeTableGenerator(req.getSignerCert());

        gen.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(digestProvider)
                        .setSignedAttributeGenerator(essGenerator)
                        .build(signer, req.getSignerCert()));

        gen.addCertificates(new JcaCertStore(chain));

        CMSTypedData typedData = new CMSProcessableByteArray(content);
        CMSSignedData signedData = gen.generate(typedData, false);
        return signedData.getEncoded();
    }

    private static class EssSignedAttributeTableGenerator extends DefaultSignedAttributeTableGenerator {
        private final X509Certificate cert;

        EssSignedAttributeTableGenerator(X509Certificate cert) {
            this.cert = cert;
        }

        @Override
        public org.bouncycastle.asn1.cms.AttributeTable getAttributes(java.util.Map parameters) {
            org.bouncycastle.asn1.cms.AttributeTable table = super.getAttributes(parameters);
            try {
                MessageDigest md = MessageDigest.getInstance(DIGEST_ALG);
                byte[] certHash = md.digest(cert.getEncoded());

                org.bouncycastle.asn1.x500.X500Name issuer =
                        new org.bouncycastle.asn1.x500.X500Name(cert.getIssuerX500Principal().getName());
                org.bouncycastle.asn1.x509.GeneralName issuerName =
                        new org.bouncycastle.asn1.x509.GeneralName(issuer);
                org.bouncycastle.asn1.x509.GeneralNames issuerNames =
                        new org.bouncycastle.asn1.x509.GeneralNames(issuerName);

                org.bouncycastle.asn1.ess.ESSCertIDv2 essCertId = new org.bouncycastle.asn1.ess.ESSCertIDv2(
                        new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                                new org.bouncycastle.asn1.ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.3")),
                        certHash,
                        new org.bouncycastle.asn1.x509.IssuerSerial(issuerNames, cert.getSerialNumber()));

                org.bouncycastle.asn1.ess.SigningCertificateV2 signingCertV2 =
                        new org.bouncycastle.asn1.ess.SigningCertificateV2(new org.bouncycastle.asn1.ess.ESSCertIDv2[]{essCertId});

                org.bouncycastle.asn1.cms.Attribute attr = new org.bouncycastle.asn1.cms.Attribute(
                        org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                        new org.bouncycastle.asn1.DERSet(signingCertV2));

                return table.add(attr.getAttrType(), attr.getAttrValues().getObjectAt(0));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to build signing-certificate-v2 attr: " + e.getMessage(), e);
            }
        }
    }
}
