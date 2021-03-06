package org.beaucatcher.mongo.jdriver

import org.beaucatcher.bson._
import org.beaucatcher.mongo._
import org.beaucatcher.driver._
import com.mongodb._
import org.bson.types.{ ObjectId => JavaObjectId, _ }
import akka.actor.ActorSystem

/**
 * [[org.beaucatcher.jdriver.JavaDriverContext]] is final with a private constructor - there's no way to create one
 * directly. Instead you call newContext() on [[org.beaucatcher.jdriver.JavaDriver]].
 * The [[org.beaucatcher.jdriver.JavaDriver]] companion object has a singleton `instance`
 * you could use for this, `JavaDriver.instance.newContext()`.
 */
final class JavaDriverContext private[jdriver] (override val driver: JavaDriver,
    val url: String, val actorSystem: ActorSystem)
    extends DriverContext {

    private lazy val jdriverURI = new MongoURI(url)

    private lazy val driverConnection = JavaDriverConnection.acquireConnection(jdriverURI)
    private def connection = driverConnection.underlying

    override type DriverType = JavaDriver
    override type DatabaseType = JavaDriverDatabase // we have no jdriver-specific Database stuff
    override type UnderlyingConnectionType = Mongo
    override type UnderlyingDatabaseType = DB
    override type UnderlyingCollectionType = DBCollection

    override def underlyingConnection: Mongo = connection
    override def underlyingDatabase: DB = connection.getDB(jdriverURI.getDatabase())
    override def underlyingCollection(name: String): DBCollection = {
        if (name == null)
            throw new IllegalArgumentException("null collection name")
        val db: DB = underlyingDatabase
        assert(db != null)
        val coll: DBCollection = db.getCollection(name)
        assert(coll != null)
        coll
    }

    override final lazy val database = {
        new JavaDriverDatabase(this)
    }

    override def close(): Unit = {
        JavaDriverConnection.releaseConnection(driverConnection)
    }
}
