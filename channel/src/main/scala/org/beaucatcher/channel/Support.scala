package org.beaucatcher.channel

import java.nio.ByteBuffer
import scala.annotation.implicitNotFound

trait EncodeSupport[-T] {
    def encode(t: T): ByteBuffer
}

trait DecodeSupport[+T] {
    def decode(buf: ByteBuffer): T
}

@implicitNotFound("Can't find an implicit QueryEncodeSupport to convert ${T} into a MongoDB query")
trait QueryEncodeSupport[-T] extends EncodeSupport[T] {

}

@implicitNotFound("Can't find an implicit EntityDecodeSupport to convert ${T} from a MongoDB document")
trait EntityDecodeSupport[+T] extends DecodeSupport[T] {

}

@implicitNotFound("Can't find an implicit EntityEncodeSupport to convert ${T} to a MongoDB document")
trait EntityEncodeSupport[-T] extends EncodeSupport[T] {

}