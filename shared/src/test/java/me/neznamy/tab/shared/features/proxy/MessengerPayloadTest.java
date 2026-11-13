package me.neznamy.tab.shared.features.proxy;

import com.saicone.delivery4j.MessageChannel;
import org.junit.Test;
import java.io.UTFDataFormatException;
import java.util.Arrays;
import static org.junit.Assert.*;

public class MessengerPayloadTest {
    @Test(expected = UTFDataFormatException.class)
    public void oldSingleArgumentTransportRejectsLargeRoster() throws Exception {
        new MessageChannel("test").encode(payload(100000));
    }

    @Test
    public void largeRosterSurvivesActualDelivery4jCodecAsOneMessage() throws Exception {
        MessageChannel channel = new MessageChannel("test");
        String original = payload(1000000);
        byte[] encoded = channel.encode((Object[]) MessengerPayload.split(original));
        assertEquals(original, MessengerPayload.join(channel.decode(encoded)));
    }

    @Test
    public void smallMessagesRemainCompatibleWithSingleArgumentReceivers() throws Exception {
        MessageChannel channel = new MessageChannel("test");
        String original = payload(1000);
        String[] decoded = channel.decode(channel.encode((Object[]) MessengerPayload.split(original)));
        assertArrayEquals(new String[]{original}, decoded);
    }

    @Test
    public void chunkBoundariesAndEmptyMessagesRoundTrip() throws Exception {
        MessageChannel channel = new MessageChannel("test");
        for (int length : new int[]{0, 60000, 60001, 65535, 65536, 120000}) {
            String original = payload(length);
            assertEquals(original, MessengerPayload.join(channel.decode(
                    channel.encode((Object[]) MessengerPayload.split(original)))));
        }
    }

    private static String payload(int size) {
        char[] text = new char[size];
        Arrays.fill(text, 'A');
        return new String(text);
    }
}
