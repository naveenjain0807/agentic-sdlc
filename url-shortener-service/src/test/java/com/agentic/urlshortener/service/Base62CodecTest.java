package com.agentic.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Base62CodecTest {

    @Test
    @DisplayName("encodes the documented boundary values")
    void encodesBoundaries() {
        assertThat(Base62Codec.encode(0L)).isEqualTo("0");
        assertThat(Base62Codec.encode(1L)).isEqualTo("1");
        assertThat(Base62Codec.encode(61L)).isEqualTo("z");
        assertThat(Base62Codec.encode(62L)).isEqualTo("10");
        assertThat(Base62Codec.encode(3843L)).isEqualTo("zz");
    }

    @Test
    @DisplayName("encode and decode round-trip")
    void roundTrips() {
        long[] samples = {0L, 1L, 61L, 62L, 12345L, 100000L, 56_800_235_583L};
        for (long sample : samples) {
            assertThat(Base62Codec.decode(Base62Codec.encode(sample))).isEqualTo(sample);
        }
    }

    @Test
    @DisplayName("padding produces a fixed width without changing the value")
    void padsToFixedWidth() {
        String padded = Base62Codec.encodePadded(1L, 6);
        assertThat(padded).hasSize(6).isEqualTo("000001");
        assertThat(Base62Codec.decode(padded)).isEqualTo(1L);
    }

    @Test
    @DisplayName("rejects negative input and illegal characters")
    void rejectsBadInput() {
        assertThatThrownBy(() -> Base62Codec.encode(-1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62Codec.decode("ab$c")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62Codec.decode("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sequential inputs scramble to distinct, non-sequential codes")
    void scrambleIsCollisionFree() {
        Set<String> codes = new HashSet<>();
        for (long seq = 100_000L; seq < 105_000L; seq++) {
            codes.add(ShortCodeGenerator.scramble(seq));
        }
        assertThat(codes).hasSize(5_000);
        assertThat(ShortCodeGenerator.scramble(100_000L))
                .isNotEqualTo(ShortCodeGenerator.scramble(100_001L))
                .hasSize(6);
    }
}
