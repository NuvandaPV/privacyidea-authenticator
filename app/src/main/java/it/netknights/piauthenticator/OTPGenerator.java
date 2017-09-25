/*
  privacyIDEA Authenticator

  Authors: Nils Behlen <nils.behlen@netknights.it>

  Copyright (c) 2017 NetKnights GmbH

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package it.netknights.piauthenticator;


import org.apache.commons.codec.binary.Base32;

import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static it.netknights.piauthenticator.Token.HOTP;
import static it.netknights.piauthenticator.Token.TOTP;


public class OTPGenerator {

    private OTPGenerator() {
    }

    private static final int[] DIGITS_POWER
            // 0 1  2   3    4     5      6       7        8
            = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000};

    /**
     * Entry point for OTP generation
     *
     * @param token to generate the OTP value for
     * @return the current OTP value for the input token
     */
    public static String generate(Token token) {
        String secretAsHEX = toHexString(new Base32().decode(token.getSecret()));
        String digits = String.valueOf(token.getDigits());

        if (token.getType().equals(TOTP)) {
            return String.format("%0" + token.getDigits() + "d", generateTOTP(secretAsHEX,
                    (System.currentTimeMillis() / 1000), digits, token.getPeriod(), token.getAlgorithm()));
        }
        if (token.getType().equals(HOTP)) {
            return String.format("%0" + token.getDigits() + "d", generateHOTP(secretAsHEX,
                    String.valueOf(token.getCounter()), digits, token.getAlgorithm()));
        }
        //  return String.format("%06d", generate(secret, System.currentTimeMillis() / 1000, 6));
        return "";
    }

    /**
     * Calculate an OTP value by the OATH TOTP algorithm as defined in RFC 6238
     *
     * @param key       The secret, shared key key
     * @param t         The unix system time, seconds since 1.1.1970
     * @param digits    The number of digits of the calculated OTP value. Would be usually either 6 or 8
     * @param period    The time step as defined in RFC. Usually 30 or 60
     * @param algorithm The hashing algorithm, "HmacSHA1", "HmacSHA256", "HmacSHA512"
     * @return The OTP value for the HOTP Token
     */
    public static int generateTOTP(String key, long t, String digits, int period, String algorithm) {
         /*
        The unix system time is devided by the time step. This number of time slices is used as
        counter input for the normal HOTP algorithm
        */
        long time = t / period;
        String step = Long.toHexString(time).toUpperCase();
        //t /= period;
        return generateHOTP(key, step, String.valueOf(digits), algorithm);
    }

    /**
     * This method generates a OTP value for the given
     * set of parameters.
     *
     * @param key:          the shared secret, HEX encoded
     * @param counter:      a value that reflects the counter (also time/period)
     * @param returnDigits: number of digits to return
     * @param crypto:       the crypto function to use
     * @return: a numeric String in base 10 that includes
     */
    public static int generateHOTP(String key,
                                   String counter,
                                   String returnDigits,
                                   String crypto) {
        int codeDigits = Integer.decode(returnDigits).intValue();
        String result = null;

        // Using the counter
        // First 8 bytes are for the movingFactor
        // Compliant with base RFC 4226 (HOTP)
        while (counter.length() < 16)
            counter = "0" + counter;

        // Get the HEX in a Byte[]
        byte[] msg = hexStr2Bytes(counter);
        byte[] k = hexStr2Bytes(key);
        byte[] hash = hmac_sha(crypto, k, msg);

        // put selected bytes into result int
        int offset = hash[hash.length - 1] & 0xf;

        int binary =
                ((hash[offset] & 0x7f) << 24) |
                        ((hash[offset + 1] & 0xff) << 16) |
                        ((hash[offset + 2] & 0xff) << 8) |
                        (hash[offset + 3] & 0xff);

        int otp = binary % DIGITS_POWER[codeDigits];

        result = Integer.toString(otp);
        while (result.length() < codeDigits) {
            result = "0" + result;
        }
        return Integer.parseInt(result);
    }

    /**
     * This method uses the JCE to provide the crypto algorithm.
     * HMAC computes a Hashed Message Authentication Code with the
     * crypto hash algorithm as a parameter.
     *
     * @param crypto:   the crypto algorithm (HmacSHA1, HmacSHA256,
     *                  HmacSHA512)
     * @param keyBytes: the bytes to use for the HMAC key
     * @param text:     the message or text to be authenticated
     */
    public static byte[] hmac_sha(String crypto, byte[] keyBytes,
                                  byte[] text) {
        try {
            Mac hmac;
            hmac = Mac.getInstance(crypto);
            SecretKeySpec macKey =
                    new SecretKeySpec(keyBytes, "RAW");
            hmac.init(macKey);
            return hmac.doFinal(text);
        } catch (GeneralSecurityException gse) {
            throw new UndeclaredThrowableException(gse);
        }
    }

    /**
     * This Method converts a byte array to a HEX String
     *
     * @param ba byte array to convert
     * @return the HEX String
     */
    public static String toHexString(byte[] ba) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < ba.length; i++)
            str.append(String.format("%02x", ba[i]));
        return str.toString();
    }

    /**
     * This method converts a HEX string to Byte[]
     *
     * @param hex: the HEX string
     * @return: a byte array
     */
    public static byte[] hexStr2Bytes(String hex) {
        // Adding one byte to get the right conversion
        // Values starting with "0" can be converted
        byte[] bArray = new BigInteger("10" + hex, 16).toByteArray();

        // Copy all the REAL bytes, not the "first"
        byte[] ret = new byte[bArray.length - 1];
        for (int i = 0; i < ret.length; i++)
            ret[i] = bArray[i + 1];
        return ret;
    }

}
