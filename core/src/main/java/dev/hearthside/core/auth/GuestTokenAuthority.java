package dev.hearthside.core.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Issues and redeems one-time guest login tokens.
 *
 * <p>Token format: {@code base64url(guestId | expiryEpochSeconds | nonce) + "." +
 * base64url(HmacSHA256(secret, firstPart))}. A token is accepted once, before it expires; every
 * other input, including garbage, is rejected without throwing.
 */
public final class GuestTokenAuthority {
	public static final Duration TTL = Duration.ofSeconds(120);

	private static final int MIN_SECRET_BYTES = 32;
	private static final int PAYLOAD_BYTES = 16 + 8 + 16;
	private static final String HMAC = "HmacSHA256";
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final SecretKeySpec key;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();
	/** Signed body of each redeemed token → its expiry in epoch seconds. */
	private final Map<String, Long> redeemed = new HashMap<>();

	public GuestTokenAuthority(byte[] secret, Clock clock) {
		if (secret.length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException("Secret must be at least " + MIN_SECRET_BYTES + " bytes");
		}
		this.key = new SecretKeySpec(secret.clone(), HMAC);
		this.clock = clock;
	}

	public static GuestTokenAuthority withRandomSecret(Clock clock) {
		byte[] secret = new byte[MIN_SECRET_BYTES];
		new SecureRandom().nextBytes(secret);
		return new GuestTokenAuthority(secret, clock);
	}

	public synchronized String issue(UUID guestId) {
		byte[] nonce = new byte[16];
		random.nextBytes(nonce);
		byte[] payload = ByteBuffer.allocate(PAYLOAD_BYTES)
				.putLong(guestId.getMostSignificantBits())
				.putLong(guestId.getLeastSignificantBits())
				.putLong(nowSeconds() + TTL.toSeconds())
				.put(nonce)
				.array();
		String body = ENCODER.encodeToString(payload);
		return body + "." + ENCODER.encodeToString(sign(body));
	}

	public synchronized Optional<UUID> redeem(String token) {
		long now = nowSeconds();
		redeemed.values().removeIf(expiry -> expiry <= now);
		if (token == null) {
			return Optional.empty();
		}
		int dot = token.indexOf('.');
		if (dot < 0 || token.indexOf('.', dot + 1) >= 0) {
			return Optional.empty();
		}
		String body = token.substring(0, dot);
		byte[] payload;
		byte[] signature;
		try {
			payload = DECODER.decode(body);
			signature = DECODER.decode(token.substring(dot + 1));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		if (payload.length != PAYLOAD_BYTES || !MessageDigest.isEqual(sign(body), signature)) {
			return Optional.empty();
		}
		ByteBuffer buf = ByteBuffer.wrap(payload);
		UUID guestId = new UUID(buf.getLong(), buf.getLong());
		long expiry = buf.getLong();
		// The signed body contains the random nonce, so it identifies this token uniquely.
		if (now >= expiry || redeemed.containsKey(body)) {
			return Optional.empty();
		}
		redeemed.put(body, expiry);
		return Optional.of(guestId);
	}

	private long nowSeconds() {
		return clock.instant().getEpochSecond();
	}

	private byte[] sign(String body) {
		try {
			Mac mac = Mac.getInstance(HMAC);
			mac.init(key);
			return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 unavailable", e);
		}
	}
}
