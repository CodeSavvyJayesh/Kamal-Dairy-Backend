package com.kamaldairy.kamal_dairy_backend.util;

import com.kamaldairy.kamal_dairy_backend.dto.DeliveryAddress;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/**
 * One set of delivery-address rules, shared by cart orders and subscriptions,
 * so a customer never sees an address accepted in one place and refused in
 * the other.
 */
public final class Addresses {

    private static final Pattern PHONE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern PINCODE = Pattern.compile("^\\d{6}$");

    private Addresses() {}

    /**
     * Validates and normalises an address: trims and collapses spaces, and
     * reduces "+91 98200 12345" or "09820012345" to "9820012345".
     *
     * @throws ApiException 400 with a message the customer can act on
     */
    public static DeliveryAddress require(DeliveryAddress a) {
        if (a == null) {
            throw bad("Add a delivery address.");
        }

        String name = clean(a.name());
        String phone = normalisePhone(a.phone());
        String address = clean(a.address());
        String city = clean(a.city());
        String pincode = clean(a.pincode());

        if (name.length() < 2 || name.length() > 80) {
            throw bad("Enter the name of the person receiving the delivery.");
        }
        if (!PHONE.matcher(phone).matches()) {
            throw bad("Enter a valid 10-digit mobile number.");
        }
        if (address.length() < 5 || address.length() > 255) {
            throw bad("Enter the full delivery address - flat, building and street.");
        }
        if (city.length() < 2 || city.length() > 60) {
            throw bad("Enter the city.");
        }
        if (!PINCODE.matcher(pincode).matches()) {
            throw bad("Enter a valid 6-digit pincode.");
        }

        return new DeliveryAddress(name, phone, address, city, pincode);
    }

    static String normalisePhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            return digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            return digits.substring(1);
        }
        return digits;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static ApiException bad(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }
}
