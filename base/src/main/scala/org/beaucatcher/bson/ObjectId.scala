/**
 * Copyright (C) 2011 Havoc Pennington
 *
 * Derived from mongo-java-driver,
 *
 *      Copyright (C) 2008 10gen Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.beaucatcher.bson

import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.Date
import scala.collection.JavaConversions.enumerationAsScalaIterator

import org.beaucatcher.util.toHex

/**
 * A globally unique identifier for objects.
 * <p>Consists of 12 bytes, divided as follows:
 * <blockquote><pre>
 * <table border="1">
 * <tr><td>0</td><td>1</td><td>2</td><td>3</td><td>4</td><td>5</td><td>6</td>
 *     <td>7</td><td>8</td><td>9</td><td>10</td><td>11</td></tr>
 * <tr><td colspan="4">time</td><td colspan="3">machine</td>
 *     <td colspan="2">pid</td><td colspan="3">inc</td></tr>
 * </table>
 * </pre></blockquote>
 */
case class ObjectId(time: Int, machine: Int, inc: Int) {

    /** Time of the ID in milliseconds (the regular time field is in seconds) */
    def timeMillis = time * 1000L

    // we cache the generated string
    private lazy val string = ObjectId.assembleString(time, machine, inc)

    // code definitely relies on this, e.g. when generating json
    override def toString = string
}

object ObjectId {

    private[bson] case class ObjectIdParts(time: Int, machine: Int, inc: Int)

    def apply(date: Date, machine: Int, inc: Int): ObjectId = {
        val time = (date.getTime() / 1000).intValue
        ObjectId(time, machine, inc)
    }

    def apply(string: String): ObjectId = {
        val parts = disassembleString(string)
        ObjectId(parts.time, parts.machine, parts.inc)
    }

    def apply(): ObjectId = {
        val time = (System.currentTimeMillis / 1000).intValue
        ObjectId(time, machine, nextInc)
    }

    private[bson] def assembleBytes(time: Int, machine: Int, inc: Int) = {
        val b = new Array[Byte](12)
        val bb = ByteBuffer.wrap(b)
        // by default BB is big endian like we need
        bb.putInt(time);
        bb.putInt(machine);
        bb.putInt(inc);
        b
    }

    private[bson] def disassembleBytes(bytes: Array[Byte]) = {
        if (bytes.length != 12)
            throw new IllegalArgumentException("BSON object ID byte[] has length " + bytes.length + " should be 12")
        val bb = ByteBuffer.wrap(bytes)
        ObjectIdParts(bb.getInt(), bb.getInt(), bb.getInt())
    }

    private[bson] def assembleString(time: Int, machine: Int, inc: Int) = {
        val bytes = assembleBytes(time, machine, inc)

        toHex(bytes)
    }

    private[bson] def disassembleString(string: String): ObjectIdParts = {
        if (string.length != 24)
            throw new IllegalArgumentException("BSON object ID string has length " + string.length + " should be 24")
        val bytes = new Array[Byte](12)
        for (i <- 0 to 11) {
            val parsed = try {
                Integer.parseInt(string.substring(i * 2, i * 2 + 2), 16)
            } catch {
                case nfe: NumberFormatException =>
                    throw new IllegalArgumentException("BSON object ID string contains invalid hex: " + string)
            }
            if (parsed < 0 || parsed > 255)
                throw new IllegalArgumentException("BSON object ID contains invalid number: " + parsed)
            val b: Byte = parsed.byteValue
            bytes.update(i, b)
        }
        disassembleBytes(bytes)
    }

    private[bson] val _nextInc = new AtomicInteger((new java.util.Random()).nextInt())

    private[bson] def nextInc = _nextInc.getAndIncrement()

    private[bson] lazy val machine = {
        // build a 2-byte machine piece based on NICs info
        val machinePiece = {
            val sb = new StringBuilder()
            for (iface <- NetworkInterface.getNetworkInterfaces()) {
                sb.append(iface.toString)
            }
            sb.toString.hashCode << 16
        }

        // add a 2 byte process piece. It must represent not only the JVM but the class loader.
        // Since static var belong to class loader there could be collisions otherwise
        val processPiece = {
            val jvmId = try {
                java.lang.management.ManagementFactory.getRuntimeMXBean().getName().hashCode()
            } catch {
                case _ => new java.util.Random().nextInt()
            }

            val loader = this.getClass().getClassLoader
            val loaderId = if (loader != null) System.identityHashCode(loader) else 0

            val sb = new StringBuilder()
            sb.append(Integer.toHexString(jvmId))
            sb.append(Integer.toHexString(loaderId))

            sb.toString.hashCode & 0xFFFF
        }

        machinePiece | processPiece
    }
}
