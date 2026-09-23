package com.kamaldairy.kamal_dairy_backend.controller;

import com.kamaldairy.kamal_dairy_backend.service.InvoiceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Turns a rendered document into an HTTP download, the same way for the customer
 * and the admin endpoint.
 *
 * The filename goes out as an RFC 6266 attachment so the browser saves
 * "Kamal-Dairy-invoice-KD-2026-27-000042.pdf" rather than the request path, and
 * the response is marked no-store: an invoice is personal, and a shared proxy
 * has no business keeping a copy.
 */
final class Invoices {

    private Invoices() {}

    static ResponseEntity<byte[]> asPdf(InvoiceService.Rendered rendered) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(rendered.pdf().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(rendered.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                // The frontend reads the number from here: a cross-origin fetch
                // cannot see a header unless it is explicitly exposed.
                .header("X-Invoice-Number", rendered.invoiceNumber() == null ? "" : rendered.invoiceNumber())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "Content-Disposition, X-Invoice-Number")
                .cacheControl(CacheControl.noStore())
                .body(rendered.pdf());
    }

    static ResponseEntity<byte[]> asCsv(String csv, String fileName) {
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(body.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
