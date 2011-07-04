package org.beaucatcher.bson

import org.bson.BSON

/**
 * A representation of BSON in JSON format.
 * Only the `CLEAN` flavor is currently implemented.
 *
 * See http://www.mongodb.org/display/DOCS/Mongo+Extended+JSON
 * Flavors JS and TenGen from that page can't be done using JsonAST
 * from lift-json, so are not supported yet.
 *
 * Flavor `CLEAN` is made up for this library, and basically strips the
 * type information from `STRICT`, so just a string object ID for example,
 * rather than the `STRICT` format which looks like:
 * {{{
 *     { "$oid" : <oid> }
 * }}}
 * This is nicer in web services perhaps. If you use a case class
 * or other approach to provide a schema for a JSON object, it isn't
 * necessary to keep type information in the JSON itself.
 */
object JsonFlavor extends Enumeration {
    type JsonFlavor = Value
    val CLEAN, STRICT, JS, TEN_GEN = Value
}

/** A detailed type tag for binary data in BSON. */
object BsonSubtype extends Enumeration {
    type BsonSubtype = Value
    val GENERAL, FUNC, BINARY, UUID = Value

    private val fromBytes =
        Map(BSON.B_GENERAL -> GENERAL,
            BSON.B_FUNC -> FUNC,
            BSON.B_BINARY -> BINARY,
            BSON.B_UUID -> UUID)

    private val toBytes = fromBytes map { _.swap }

    def fromByte(b : Byte) : Option[BsonSubtype] = {
        fromBytes.get(b)
    }

    def toByte(v : Value) : Byte = {
        toBytes.getOrElse(v, throw new IllegalArgumentException("bad BsonSubtype value"))
    }
}

/** Types that appear in the BSON wire protocol. See [[http://bsonspec.org/]] for more. */
object BsonType extends Enumeration {
    type BsonType = Value

    /* These names match the ones in mongo-java-driver */
    val EOO, NUMBER, STRING, OBJECT, ARRAY, BINARY, UNDEFINED, OID, BOOLEAN, DATE, NULL, REGEX, REF, CODE, SYMBOL, CODE_W_SCOPE, NUMBER_INT, TIMESTAMP, NUMBER_LONG, MINKEY, MAXKEY = Value

    private val fromBytes =
        Map(BSON.EOO -> EOO,
            BSON.NUMBER -> NUMBER,
            BSON.STRING -> STRING,
            BSON.OBJECT -> OBJECT,
            BSON.ARRAY -> ARRAY,
            BSON.BINARY -> BINARY,
            BSON.UNDEFINED -> UNDEFINED,
            BSON.OID -> OID,
            BSON.BOOLEAN -> BOOLEAN,
            BSON.DATE -> DATE,
            BSON.NULL -> NULL,
            BSON.REGEX -> REGEX,
            BSON.REF -> REF,
            BSON.CODE -> CODE,
            BSON.SYMBOL -> SYMBOL,
            BSON.CODE_W_SCOPE -> CODE_W_SCOPE,
            BSON.NUMBER_INT -> NUMBER_INT,
            BSON.TIMESTAMP -> TIMESTAMP,
            BSON.NUMBER_LONG -> NUMBER_LONG,
            BSON.MINKEY -> MINKEY,
            BSON.MAXKEY -> MAXKEY)

    private val toBytes = fromBytes map { _.swap }

    /**
     * Convert from the byte found in the wire protocol to the [[org.beaucatcher.bson.BsonType]] enumeration.
     * @param b the byte
     * @return a [[org.beaucatcher.bson.BsonType]] or `None`
     */
    def fromByte(b : Byte) : Option[BsonType] = {
        fromBytes.get(b)
    }

    /**
     * Convert to the wire protocol byte for the given type.
     * @param v the [[org.beaucatcher.bson.BsonType]] enumeration value
     * @return a wire protocol byte
     */
    def toByte(v : Value) : Byte = {
        toBytes.getOrElse(v, throw new IllegalArgumentException("bad BsonType value"))
    }
}
