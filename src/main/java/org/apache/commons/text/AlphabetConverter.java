/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.text;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * <p>
 * Convert from one alphabet to another, with the possibility of leaving certain characters unencoded.
 * </p>
 *
 * <p>
 * The target and do not encode languages must be in the Unicode BMP, but the source language does not.
 * </p>
 *
 * <p>
 * The encoding will all be of a fixed length, except for the 'do not encode' chars, which will be of length 1
 * </p>
 *
 * <h3>Sample usage</h3>
 *
 * <pre>
 * Set<Character> originals; // a, b, c, d
 * Set<Character> encoding; // 0, 1, d
 * Set<Character> doNotEncode; // d
 *
 * AlphabetConverter ac = AlphabetConverter.createConverter(originals, encoding, doNotEncode);
 *
 * ac.encode("a"); // 00
 * ac.encode("b"); // 01
 * ac.encode("c"); // 0d
 * ac.encode("d"); // d
 * ac.encode("abcd"); // 00010dd
 * </pre>
 *
 * @since 0.1
 */
public class AlphabetConverter {

    private final Map<Integer, String> originalToEncoded;
    private final Map<String, String> encodedToOriginal;

    private final int encodedLetterLength;

    private static final String ARROW = " -> ";
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * Hidden constructor for alphabet converter. Used by static helper methods.
     *
     * @param originalToEncoded original string to be encoded
     * @param encodedToOriginal encoding alphabet
     * @param doNotEncodeMap encoding black list
     * @param encodedLetterLength length of the encoded letter
     */
    private AlphabetConverter(Map<Integer, String> originalToEncoded, Map<String, String> encodedToOriginal,
            Map<Integer, String> doNotEncodeMap, int encodedLetterLength) {

        this.originalToEncoded = new ConcurrentHashMap<>(originalToEncoded);
        this.encodedToOriginal = new ConcurrentHashMap<>(encodedToOriginal);
        this.encodedLetterLength = encodedLetterLength;
    }

    /**
     * Encode a given string.
     *
     * @param original the string to be encoded
     * @return the encoded string
     * @throws UnsupportedEncodingException if chars that are not supported are encountered
     */
    public String encode(String original) throws UnsupportedEncodingException {
        if (original == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < original.length();) {
            int codepoint = original.codePointAt(i);

            String nextLetter = originalToEncoded.get(codepoint);

            if (nextLetter == null) {
                throw new UnsupportedEncodingException(
                        "Couldn't find encoding for '" + codePointToString(codepoint) + "' in " + original);
            }

            sb.append(nextLetter);

            i += Character.charCount(codepoint);
        }

        return sb.toString();
    }

    /**
     * Get the length of characters in the encoded alphabet that are necessaryfor each character in the original
     * alphabet.
     *
     * @return the length of the encoded char
     */
    public int getEncodedCharLength() {
        return encodedLetterLength;
    }

    /**
     * Get the mapping from integer code point of source language to encoded string. Use to reconstruct converter from
     * serialized map
     *
     * @return the original map
     */
    public Map<Integer, String> getOriginalToEncoded() {
        return Collections.unmodifiableMap(originalToEncoded);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        originalToEncoded.forEach((key, value) -> {
            sb.append(codePointToString(key)).append(ARROW).append(value).append(LINE_SEPARATOR);
        });

        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj instanceof AlphabetConverter == false) {
            return false;
        }
        final AlphabetConverter other = (AlphabetConverter) obj;
        return originalToEncoded.equals(other.originalToEncoded) && encodedToOriginal.equals(other.encodedToOriginal)
                && encodedLetterLength == other.encodedLetterLength;
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalToEncoded, encodedToOriginal, encodedLetterLength);
    }

    // -- static methods

    /**
     * Create a new converter from a map.
     *
     * @param originalToEncoded a map returned from getOriginalToEncoded()
     * @return the reconstructed AlphabetConverter
     * @see AlphabetConverter#getOriginalToEncoded()
     */
    public static AlphabetConverter createConverterFromMap(Map<Integer, String> originalToEncoded) {
        final Map<Integer, String> unmodifiableOriginalToEncoded = Collections.unmodifiableMap(originalToEncoded);
        Map<String, String> encodedToOriginal = new LinkedHashMap<>();
        Map<Integer, String> doNotEncodeMap = new HashMap<>();

        int encodedLetterLength = 1;

        for (Entry<Integer, String> e : unmodifiableOriginalToEncoded.entrySet()) {
            String originalAsString = codePointToString(e.getKey());
            encodedToOriginal.put(e.getValue(), originalAsString);

            if (e.getValue().equals(originalAsString)) {
                doNotEncodeMap.put(e.getKey(), e.getValue());
            }

            if (e.getValue().length() > encodedLetterLength) {
                encodedLetterLength = e.getValue().length();
            }
        }

        return new AlphabetConverter(unmodifiableOriginalToEncoded, encodedToOriginal, doNotEncodeMap,
                encodedLetterLength);
    }

    /**
     * Create an alphabet converter, for converting from the original alphabet, to the encoded alphabet, while leaving
     * the characters in <em>doNotEncode</em> as they are (if possible).
     *
     * @param original a Set of chars representing the original alphabet
     * @param encoding a Set of chars representing the alphabet to be used for encoding
     * @param doNotEncode a Set of chars to be encoded using the original alphabet - every char here must appear in both
     *            the previous params
     * @return the AlphabetConverter
     * @throws IllegalArgumentException if an AlphabetConverter cannot be constructed
     */
    public static AlphabetConverter createConverterFromChars(Set<Character> original, Set<Character> encoding,
            Set<Character> doNotEncode) {
        return AlphabetConverter.createConverter(convertCharsToIntegers(original), convertCharsToIntegers(encoding),
                convertCharsToIntegers(doNotEncode));
    }

    private static Set<Integer> convertCharsToIntegers(Set<Character> chars) {
        Set<Integer> integers = chars.stream().map(c -> {
            return (int) c;
        }).collect(Collectors.toSet());
        return integers;
    }

    /**
     * Create an alphabet converter, for converting from the original alphabet, to the encoded alphabet, while leaving
     * the characters in <em>doNotEncode</em> as they are (if possible)
     *
     * @param original a Set of ints representing the original alphabet in codepoints
     * @param encoding a Set of ints representing the alphabet to be used for encoding, in codepoints
     * @param doNotEncode a Set of ints representing the chars to be encoded using the original alphabet - every char
     *            here must appear in both the previous params
     * @return the AlphabetConverter
     * @throws IllegalArgumentException if an AlphabetConverter cannot be constructed
     */
    public static AlphabetConverter createConverter(Set<Integer> original, Set<Integer> encoding,
            Set<Integer> doNotEncode) {

        final Map<Integer, String> originalToEncoded = new LinkedHashMap<>();
        final Map<String, String> encodedToOriginal = new LinkedHashMap<>();
        final Map<Integer, String> doNotEncodeMap = new HashMap<>();

        int encodedLetterLength;

        for (int i : doNotEncode) {
            if (!original.contains(i)) {
                throw new IllegalArgumentException(
                        "Can not use 'do not encode' list because original alphabet does not contain '"
                                + codePointToString(i) + "'");
            }

            if (!encoding.contains(i)) {
                throw new IllegalArgumentException(
                        "Can not use 'do not encode' list because encoding alphabet does not contain '"
                                + codePointToString(i) + "'");
            }

            doNotEncodeMap.put(i, codePointToString(i));
        }

        if (encoding.size() >= original.size()) {
            encodedLetterLength = 1;

            Iterator<Integer> it = encoding.iterator();

            for (int originalLetter : original) {
                String originalLetterAsString = codePointToString(originalLetter);

                if (doNotEncodeMap.containsKey(originalLetter)) {
                    originalToEncoded.put(originalLetter, originalLetterAsString);
                    encodedToOriginal.put(originalLetterAsString, originalLetterAsString);
                } else {
                    Integer next = it.next();

                    while (doNotEncode.contains(next)) {
                        next = it.next();
                    }

                    String encodedLetter = codePointToString(next);

                    originalToEncoded.put(originalLetter, encodedLetter);
                    encodedToOriginal.put(encodedLetter, originalLetterAsString);
                }
            }

            return new AlphabetConverter(originalToEncoded, encodedToOriginal, doNotEncodeMap, encodedLetterLength);

        } else if (encoding.size() - doNotEncode.size() < 2) {
            throw new IllegalArgumentException(
                    "Must have at least two encoding characters (not counting those in the 'do not encode' list), but has  "
                            + (encoding.size() - doNotEncode.size()));
        } else {
            // we start with one which is our minimum, and because we do the
            // first division outside the loop
            int lettersSoFar = 1;

            // the first division takes into account that the doNotEncode
            // letters can't be in the leftmost place
            int lettersLeft = (original.size() - doNotEncode.size()) / (encoding.size() - doNotEncode.size());

            while (lettersLeft / encoding.size() >= 1) {
                lettersLeft = lettersLeft / encoding.size();
                lettersSoFar++;
            }

            encodedLetterLength = lettersSoFar + 1;

            AlphabetConverter ac = new AlphabetConverter(originalToEncoded, encodedToOriginal, doNotEncodeMap,
                    encodedLetterLength);

            ac.addSingleEncoding(encodedLetterLength, "", encoding, original.iterator(), doNotEncodeMap);

            return ac;
        }
    }

    /**
     * Decodes a given string
     *
     * @param encoded a string that has been encoded using this AlphabetConverter
     * @return the decoded string such that AlphabetConverter.encode() will return encoded
     * @throws UnsupportedEncodingException if unexpected characters that cannot be handled are encountered
     */
    public String decode(String encoded) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();

        for (int j = 0; j < encoded.length();) {
            Integer i = encoded.codePointAt(j);
            String s = codePointToString(i);

            if (s.equals(originalToEncoded.get(i))) {
                result.append(s);
                j++; // because we do not encode in Unicode extended the length of each encoded char is 1
            } else {
                if (j + encodedLetterLength > encoded.length()) {
                    throw new UnsupportedEncodingException("Unexpected end of string while decoding " + encoded);
                } else {
                    String nextGroup = encoded.substring(j, j + encodedLetterLength);
                    String next = encodedToOriginal.get(nextGroup);
                    if (next == null) {
                        throw new UnsupportedEncodingException(
                                "Unexpected string without decoding (" + nextGroup + ") in " + encoded);
                    } else {
                        result.append(next);
                        j += encodedLetterLength;
                    }
                }
            }
        }

        return result.toString();
    }

    /**
     * Recursive method used when creating encoder/decoder
     */
    private void addSingleEncoding(int level, String currentEncoding, Collection<Integer> encoding,
            Iterator<Integer> originals, Map<Integer, String> doNotEncodeMap) {

        if (level > 0) {
            for (int encodingLetter : encoding) {
                if (originals.hasNext()) {

                    // this skips the doNotEncode chars if they are in the
                    // leftmost place
                    if (level != encodedLetterLength || !doNotEncodeMap.containsKey(encodingLetter)) {
                        addSingleEncoding(level - 1, currentEncoding + codePointToString(encodingLetter), encoding,
                                originals, doNotEncodeMap);
                    }
                } else {
                    return; // done encoding all the original alphabet
                }
            }
        } else {
            Integer next = originals.next();

            while (doNotEncodeMap.containsKey(next)) {
                String originalLetterAsString = codePointToString(next);

                originalToEncoded.put(next, originalLetterAsString);
                encodedToOriginal.put(originalLetterAsString, originalLetterAsString);

                if (!originals.hasNext()) {
                    return;
                }

                next = originals.next();
            }

            String originalLetterAsString = codePointToString(next);

            originalToEncoded.put(next, currentEncoding);
            encodedToOriginal.put(currentEncoding, originalLetterAsString);
        }
    }

    /**
     * Create new String that contains just the given code point.
     *
     * @param i code point
     * @return a new string with the new code point
     * @see http://www.oracle.com/us/technologies/java/supplementary-142654.html
     */
    private static String codePointToString(int i) {
        if (Character.charCount(i) == 1) {
            return String.valueOf((char) i);
        } else {
            return new String(Character.toChars(i));
        }
    }

}
