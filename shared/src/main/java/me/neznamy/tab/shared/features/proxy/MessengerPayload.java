package me.neznamy.tab.shared.features.proxy;

/** Delivery4j encodes each argument with writeUTF (at most 65535 bytes).
 * TAB payloads are Base64 ASCII. Split into arguments of ONE atomic broker message,
 * so a large roster cannot be lost or reconciled as several partial rosters.
 */
final class MessengerPayload {
    private static final int CHUNK_SIZE = 60000;

    static String[] split(String base64) {
        String[] parts = new String[Math.max(1, (base64.length() + CHUNK_SIZE - 1) / CHUNK_SIZE)];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = base64.substring(i * CHUNK_SIZE, Math.min(base64.length(), (i + 1) * CHUNK_SIZE));
        }
        return parts;
    }

    static String join(String[] parts) {
        return String.join("", parts);
    }
}
