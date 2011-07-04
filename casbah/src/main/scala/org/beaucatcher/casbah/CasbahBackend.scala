package org.beaucatcher.casbah

import org.beaucatcher.bson._
import org.beaucatcher.mongo._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala._
import org.joda.time.DateTime

private[casbah] class CasbahBackend(private val databaseName : String,
    private val host : String, private val port : Int) extends MongoBackend {

    private lazy val connection = CasbahBackend.connections.ensure(MongoConnectionAddress(host, port))

    private def collection(name : String) : MongoCollection = {
        if (name == null)
            throw new IllegalArgumentException("null collection name")
        val db : MongoDB = connection(databaseName)
        assert(db != null)
        val coll : MongoCollection = db(name)
        assert(coll != null)
        coll
    }

    override final def createDAOGroup[EntityType <: Product : Manifest, IdType](collectionName : String,
        caseClassBObjectQueryComposer : QueryComposer[BObject, BObject],
        caseClassBObjectEntityComposer : EntityComposer[EntityType, BObject]) : SyncDAOGroup[EntityType, IdType, IdType] = {
        new CaseClassBObjectCasbahDAOGroup[EntityType, IdType, IdType](collection(collectionName),
            caseClassBObjectQueryComposer,
            caseClassBObjectEntityComposer,
            new IdentityIdComposer[IdType])
    }
}

private[casbah] object CasbahBackend {

    val connections = new MongoConnectionStore[MongoConnection] {
        override def create(address : MongoConnectionAddress) = {
            RegisterJodaTimeConversionHelpers()

            val c = MongoConnection(address.host, address.port)
            // things are awfully race-prone without Safe, and you
            // don't get constraint violations for example
            c.setWriteConcern(WriteConcern.Safe)
            c
        }
    }

}

/**
 * Mix this trait into a subclass of [[org.beaucatcher.mongo.CollectionOperations]] to backend
 * the collection operations using Casbah
 */
trait CasbahBackendProvider extends MongoBackendProvider {
    self : MongoConfigProvider =>

    override lazy val backend : MongoBackend = {
        require(mongoConfig != null)
        new CasbahBackend(mongoConfig.databaseName, mongoConfig.host, mongoConfig.port)
    }
}