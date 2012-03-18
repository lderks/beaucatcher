package org.beaucatcher.jdriver

import org.beaucatcher.bson._
import org.beaucatcher.mongo._
import com.mongodb._
import org.bson.types.{ ObjectId => JavaObjectId, _ }
import org.joda.time.DateTime
import akka.actor.ActorSystem

/**
 * [[org.beaucatcher.jdriver.JavaDriverContext]] is final with a private constructor - there's no way to create one
 * directly. Instead you call newContext() on the [[org.beaucatcher.jdriver.JavaDriver]].
 */
final class JavaDriverContext private[jdriver] (override val driver : JavaDriver,
    override val config : MongoConfig,
    override val actorSystem : ActorSystem)
    extends Context {

    private lazy val jdriverURI = new MongoURI(config.url)
    private lazy val connection = {
        val c = new Mongo(new MongoURI(config.url))
        // things are awfully race-prone without Safe, and you
        // don't get constraint violations for example
        c.setWriteConcern(WriteConcern.SAFE)
        c
    }

    override type DriverType = JavaDriver
    override type DatabaseType = Database // we have no jdriver-specific Database stuff
    override type UnderlyingConnectionType = Mongo
    override type UnderlyingDatabaseType = DB
    override type UnderlyingCollectionType = DBCollection

    override def underlyingConnection : Mongo = connection
    override def underlyingDatabase : DB = connection.getDB(jdriverURI.getDatabase())
    override def underlyingCollection(name : String) : DBCollection = {
        if (name == null)
            throw new IllegalArgumentException("null collection name")
        val db : DB = underlyingDatabase
        assert(db != null)
        val coll : DBCollection = db.getCollection(name)
        assert(coll != null)
        coll
    }

    override final lazy val database = {
        new JavaDriverDatabase(this)
    }

    override def close() : Unit = {
        throw new BugInSomethingMongoException("Need to implement close()"); // FIXME
    }
}
