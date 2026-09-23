package com.kamaldairy.kamal_dairy_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Who is selling, as it must appear on the invoice. All of it comes from
 * configuration (app.invoice.*) because it is business identity, not code, and
 * it changes when the dairy registers, moves or renews a licence.
 *
 * The one setting that changes the document itself is {@code gstin}. With a
 * GSTIN the dairy is registered and issues a TAX INVOICE carrying CGST and SGST.
 * Without one it is not registered, so charging or showing GST would be wrong,
 * and the same order produces a BILL OF SUPPLY with no tax columns at all.
 */
@Component
@ConfigurationProperties(prefix = "app.invoice")
public class InvoiceProperties {

    /** Legal name of the seller. */
    private String sellerName = "Kamal Dairy";

    /** Trade name shown large at the top, if it differs from the legal name. */
    private String tradeName = "";

    /** GSTIN. Blank means not registered: the document becomes a bill of supply. */
    private String gstin = "";

    /** FSSAI licence number, which a dairy has to print. Optional. */
    private String fssai = "";

    private String addressLine1 = "";
    private String addressLine2 = "";
    private String city = "";
    private String state = "";
    private String pincode = "";
    private String phone = "";
    private String email = "";

    /** Leading segment of every invoice number. */
    private String prefix = "KD";

    /** Printed under the totals. */
    private String declaration =
            "We declare that this invoice shows the actual price of the goods described "
            + "and that all particulars are true and correct.";

    /** Terms line, e.g. return window or delivery note. Optional. */
    private String terms = "";

    /** Name signed against, under the signature rule. */
    private String signatory = "";

    public boolean isGstRegistered() {
        return gstin != null && !gstin.isBlank();
    }

    /** Trade name if set, otherwise the legal name. */
    public String displayName() {
        return tradeName == null || tradeName.isBlank() ? sellerName : tradeName;
    }

    public String getSellerName() { return sellerName; }

    public void setSellerName(String sellerName) { this.sellerName = sellerName; }

    public String getTradeName() { return tradeName; }

    public void setTradeName(String tradeName) { this.tradeName = tradeName; }

    public String getGstin() { return gstin; }

    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getFssai() { return fssai; }

    public void setFssai(String fssai) { this.fssai = fssai; }

    public String getAddressLine1() { return addressLine1; }

    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }

    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }

    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }

    public void setState(String state) { this.state = state; }

    public String getPincode() { return pincode; }

    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getPhone() { return phone; }

    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getPrefix() { return prefix == null || prefix.isBlank() ? "KD" : prefix.trim(); }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getDeclaration() { return declaration; }

    public void setDeclaration(String declaration) { this.declaration = declaration; }

    public String getTerms() { return terms; }

    public void setTerms(String terms) { this.terms = terms; }

    public String getSignatory() { return signatory; }

    public void setSignatory(String signatory) { this.signatory = signatory; }
}
