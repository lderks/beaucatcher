package org.beaucatcher.mongo.cdriver

import org.beaucatcher.bson.Implicits._
import org.beaucatcher.bson._
import org.beaucatcher.mongo._
import org.beaucatcher.wire.mongo._
import org.beaucatcher.channel.netty._
import org.beaucatcher.mongo.cdriver._
import org.junit.Assert._
import org.junit._
import java.nio.ByteOrder
import org.jboss.netty.buffer.ChannelBuffers
import java.util.Random

class SerializerTest extends TestUtils {

    private def testRoundTrip(bobj: BObject): Unit = {
        import Support._

        val buf = ChannelBuffers.dynamicBuffer(ByteOrder.LITTLE_ENDIAN, 128)

        writeQuery(buf, bobj, DEFAULT_MAX_DOCUMENT_SIZE)

        /*
        System.err.println("Wrote " + bobj + " to buffer: " + buf)

        buf.resetReaderIndex()
        var i = 0
        while (i < buf.writerIndex()) {
            if (i % 4 == 0)
                System.err.println("  " + buf.getInt(i))
            val b = buf.getByte(i)
            System.err.println("" + i + ": 0x" + Integer.toHexString(b))
            if (Character.isLetter(b))
                System.err.println("    " + Character.toString(b.toChar))
            i += 1
        }
        */

        buf.resetReaderIndex()
        val decoded = readEntity[BObject](buf)

        assertEquals(bobj, decoded)
    }

    @Test
    def roundTripBObject(): Unit = {
        val many = BsonTest.makeObjectManyTypes()
        // first test each field separately
        for (field <- many.value) {
            testRoundTrip(BObject(List(field)))
        }
        // then test the whole giant object
        testRoundTrip(many)
    }

    @Test
    def growBufferWhenNeeded(): Unit = {
        import Support._

        val rand = new Random(1234L)
        val buf = ChannelBuffers.dynamicBuffer(ByteOrder.LITTLE_ENDIAN, 1)

        val many = BsonTest.makeObjectManyTypes()

        for (i <- 1 to 100) {
            // nextInt(x) is exclusive of x, so we write between 0 and 7
            var j = rand.nextInt(8)
            while (j > 0) {
                buf.ensureWritableBytes(1)
                buf.writeByte('X')
                j -= 1
            }
            for (field <- many.value) {
                writeQuery(buf, BObject(List(field)), DEFAULT_MAX_DOCUMENT_SIZE)
            }
            writeQuery(buf, many, DEFAULT_MAX_DOCUMENT_SIZE)
        }
    }
}