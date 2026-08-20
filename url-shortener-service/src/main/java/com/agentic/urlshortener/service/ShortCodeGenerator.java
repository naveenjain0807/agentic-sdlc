package com.agentic.urlshortener.service;

import com.agentic.urlshortener.repository.ShortUrlRepository;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Generates short codes from a database sequence.
 *
 * <p>The raw sequence value is passed through a multiplicative permutation
 * (multiply by a constant coprime with 62^6, modulo 62^6). That mapping is a
 * bijection, so codes stay collision-free while no longer looking sequential -
 * you cannot enumerate other people's links by incrementing a code.
 *
 * <p>Swapping the sequence for a Redis {@code INCR} later is a one-line change:
 * only {@link #nextRawValue()} touches the counter.
 */
@Component
public class ShortCodeGenerator {

    /** 62^6 - the code space for six-character codes. */
    static final long CODE_SPACE = 56_800_235_584L;

    /** Knuth's multiplicative constant; odd and not divisible by 31, hence coprime with 62^6 = 2^6 * 31^6. */
    static final long MULTIPLIER = 2_654_435_761L;

    /** Codes that would shadow application routes and must never be handed out. */
    public static final Set<String> RESERVED_CODES = Set.of(
            "api", "actuator", "swagger", "swagger-ui", "v3", "h2-console", "shorten",
            "health", "metrics", "admin", "static", "assets", "error", "favicon", "login");

    private final ShortUrlRepository shortUrlRepository;

    public ShortCodeGenerator(ShortUrlRepository shortUrlRepository) {
        this.shortUrlRepository = shortUrlRepository;
    }

    public String nextCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = scramble(nextRawValue());
            if (!RESERVED_CODES.contains(candidate.toLowerCase()) && !shortUrlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique short code after 5 attempts");
    }

    protected long nextRawValue() {
        return shortUrlRepository.nextShortCodeSequence();
    }

    static String scramble(long sequenceValue) {
        long permuted = Math.floorMod(sequenceValue * MULTIPLIER, CODE_SPACE);
        return Base62Codec.encodePadded(permuted, 6);
    }
}
